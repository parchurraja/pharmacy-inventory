package com.pharmacy.inventory.service;

import com.pharmacy.inventory.dto.MedicineDTO;
import com.pharmacy.inventory.dto.PageResponse;
import com.pharmacy.inventory.entity.*;
import com.pharmacy.inventory.exception.*;
import com.pharmacy.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service managing sales with concurrency-safe FEFO (First-Expired, First-Out) stock deduction,
 * sales returns, and transaction traceability.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SaleService {

    private final MedicineRepository medicineRepository;
    private final MedicineBatchRepository batchRepository;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. CONCURRENCY-SAFE FEFO STOCK DEDUCTION & SALE CREATION
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(rollbackFor = Exception.class)
    public MedicineDTO.SaleResponseDTO processSale(MedicineDTO.SaleCreateRequest request, String soldBy) {
        log.info("Processing sale for customer: {}, sold by: {}, items count: {}",
                request.getCustomerName(), soldBy, request.getItems().size());

        String invoiceNumber = generateInvoiceNumber();
        Sale sale = Sale.builder()
                .invoiceNumber(invoiceNumber)
                .saleDate(LocalDateTime.now())
                .totalAmount(BigDecimal.ZERO)
                .customerName(request.getCustomerName())
                .soldBy(soldBy)
                .status(SaleStatus.COMPLETED)
                .items(new ArrayList<>())
                .build();

        BigDecimal runningTotal = BigDecimal.ZERO;

        for (MedicineDTO.SaleItemRequest itemReq : request.getItems()) {
            Medicine medicine;
            if (itemReq.getMedicineId() != null) {
                medicine = medicineRepository.findById(itemReq.getMedicineId())
                        .orElseThrow(() -> new MedicineNotFoundException("ID: " + itemReq.getMedicineId()));
            } else if (itemReq.getMedicineName() != null) {
                medicine = medicineRepository.findByNameIgnoreCase(itemReq.getMedicineName().trim())
                        .orElseThrow(() -> new MedicineNotFoundException(itemReq.getMedicineName()));
            } else {
                throw new IllegalArgumentException("Either medicineId or medicineName must be specified for each sale item");
            }

            if (!medicine.isActive()) {
                throw new IllegalArgumentException("Medicine is deactivated and cannot be sold: " + medicine.getName());
            }

            // Concurrency-safe: Acquire pessimistic write lock (SELECT ... FOR UPDATE) on available batches
            // Batches are returned ordered by expiryDate ASC -> FEFO (First-Expired, First-Out)
            List<MedicineBatch> lockedBatches = batchRepository.findAvailableBatchesForUpdate(medicine.getId());

            // Exclude expired batches - pharmaceutical safety constraint
            List<MedicineBatch> validBatches = lockedBatches.stream()
                    .filter(b -> !b.isExpired())
                    .collect(Collectors.toList());

            if (lockedBatches.isEmpty() || validBatches.isEmpty()) {
                if (!lockedBatches.isEmpty()) {
                    throw new ExpiredMedicineException(medicine.getName());
                }
                throw new InsufficientStockException(medicine.getName(), itemReq.getQuantity(), 0);
            }

            int totalAvailable = validBatches.stream().mapToInt(MedicineBatch::getQuantity).sum();
            if (totalAvailable < itemReq.getQuantity()) {
                throw new InsufficientStockException(medicine.getName(), itemReq.getQuantity(), totalAvailable);
            }

            // FEFO Deduction across batches
            int remainingNeeded = itemReq.getQuantity();

            for (MedicineBatch batch : validBatches) {
                if (remainingNeeded <= 0) break;

                int taken = Math.min(batch.getQuantity(), remainingNeeded);

                BigDecimal unitPrice = batch.getSellingPrice() != null
                        ? batch.getSellingPrice()
                        : BigDecimal.valueOf(10.00);

                BigDecimal costPrice = batch.getCostPrice() != null
                        ? batch.getCostPrice()
                        : BigDecimal.ZERO;

                BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(taken))
                        .setScale(2, java.math.RoundingMode.HALF_UP);
                runningTotal = runningTotal.add(subtotal);

                // Deduct inventory from batch
                batch.setQuantity(batch.getQuantity() - taken);
                batchRepository.save(batch);

                SaleItem saleItem = SaleItem.builder()
                        .sale(sale)
                        .medicine(medicine)
                        .batch(batch)
                        .quantity(taken)
                        .unitPrice(unitPrice)
                        .costPrice(costPrice)
                        .subtotal(subtotal)
                        .returnedQuantity(0)
                        .build();

                sale.getItems().add(saleItem);
                remainingNeeded -= taken;

                log.info("FEFO Allocated {} units of {} from batch {} (expiry: {}). Remaining in batch: {}",
                        taken, medicine.getName(), batch.getBatchNumber(), batch.getExpiryDate(), batch.getQuantity());
            }
        }

        sale.setTotalAmount(runningTotal);
        Sale savedSale = saleRepository.save(sale);

        log.info("Sale completed successfully with invoice: {}, total: {}", savedSale.getInvoiceNumber(), savedSale.getTotalAmount());
        return mapToSaleResponseDTO(savedSale);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. SALES RETURN — RESTORE RETURNED STOCK TO BATCHES
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(rollbackFor = Exception.class)
    public MedicineDTO.SaleReturnResponse processReturn(
            Long saleId,
            MedicineDTO.SaleReturnRequest request,
            String adjustedBy) {

        log.info("Processing return for sale ID: {}, adjusted by: {}", saleId, adjustedBy);

        Sale sale = saleRepository.findByIdForUpdate(saleId)
                .orElseThrow(() -> new SaleNotFoundException(saleId));

        if (sale.getStatus() == SaleStatus.REFUNDED) {
            throw new InvalidSaleReturnException("Sale has already been fully refunded: " + sale.getInvoiceNumber());
        }

        int totalUnitsReturned = 0;
        BigDecimal totalRefundAmount = BigDecimal.ZERO;

        for (MedicineDTO.ReturnItemRequest itemReq : request.getItems()) {
            SaleItem saleItem = sale.getItems().stream()
                    .filter(i -> i.getId().equals(itemReq.getSaleItemId()))
                    .findFirst()
                    .orElseThrow(() -> new InvalidSaleReturnException(
                            "Sale item ID " + itemReq.getSaleItemId() + " does not belong to sale ID " + saleId));

            int availableForReturn = saleItem.getAvailableForReturn();
            if (itemReq.getQuantity() > availableForReturn) {
                throw new InvalidSaleReturnException(String.format(
                        "Cannot return %d units for item ID %d. Only %d units available for return.",
                        itemReq.getQuantity(), saleItem.getId(), availableForReturn));
            }

            // Lock batch and restore returned quantity
            MedicineBatch batch = batchRepository.findByIdForUpdate(saleItem.getBatch().getId())
                    .orElseThrow(() -> new BatchNotFoundException(saleItem.getBatch().getId()));

            batch.setQuantity(batch.getQuantity() + itemReq.getQuantity());
            batchRepository.save(batch);

            // Audit record in stock_adjustments
            StockAdjustment adjustment = StockAdjustment.builder()
                    .batch(batch)
                    .adjustmentType(AdjustmentType.RETURN)
                    .quantityAdjusted(itemReq.getQuantity())
                    .reason(request.getReason() != null ? request.getReason() : "Customer sales return for invoice " + sale.getInvoiceNumber())
                    .adjustedBy(adjustedBy)
                    .adjustedAt(LocalDateTime.now())
                    .build();
            stockAdjustmentRepository.save(adjustment);

            saleItem.setReturnedQuantity(saleItem.getReturnedQuantity() + itemReq.getQuantity());
            saleItemRepository.save(saleItem);

            totalUnitsReturned += itemReq.getQuantity();
            BigDecimal refundForItem = saleItem.getUnitPrice().multiply(BigDecimal.valueOf(itemReq.getQuantity()))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            totalRefundAmount = totalRefundAmount.add(refundForItem);
        }

        // Determine if fully or partially refunded
        boolean allFullyReturned = sale.getItems().stream()
                .allMatch(item -> item.getReturnedQuantity() == item.getQuantity());

        sale.setStatus(allFullyReturned ? SaleStatus.REFUNDED : SaleStatus.PARTIALLY_REFUNDED);
        saleRepository.save(sale);

        return MedicineDTO.SaleReturnResponse.builder()
                .invoiceNumber(sale.getInvoiceNumber())
                .status(sale.getStatus())
                .totalQuantityReturned(totalUnitsReturned)
                .refundAmount(totalRefundAmount)
                .message(String.format("Successfully processed return of %d items for refund amount %s",
                        totalUnitsReturned, totalRefundAmount))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. SALES HISTORY & REPORTING (PAGINATED)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PageResponse<MedicineDTO.SaleResponseDTO> getSalesHistory(Pageable pageable) {
        Page<Sale> page = saleRepository.findAll(pageable);
        return PageResponse.from(page, this::mapToSaleResponseDTO);
    }

    @Transactional(readOnly = true)
    public MedicineDTO.SaleResponseDTO getSaleById(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new SaleNotFoundException(id));
        return mapToSaleResponseDTO(sale);
    }

    @Transactional(readOnly = true)
    public MedicineDTO.SaleResponseDTO getSaleByInvoice(String invoiceNumber) {
        Sale sale = saleRepository.findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() -> new SaleNotFoundException(invoiceNumber));
        return mapToSaleResponseDTO(sale);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPER METHODS
    // ─────────────────────────────────────────────────────────────────────────

    private String generateInvoiceNumber() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String shortId = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return "INV-" + timestamp + "-" + shortId;
    }

    public MedicineDTO.SaleResponseDTO mapToSaleResponseDTO(Sale sale) {
        List<MedicineDTO.SaleItemResponseDTO> itemDTOs = sale.getItems().stream()
                .map(i -> MedicineDTO.SaleItemResponseDTO.builder()
                        .id(i.getId())
                        .medicineId(i.getMedicine() != null ? i.getMedicine().getId() : null)
                        .medicineName(i.getMedicine() != null ? i.getMedicine().getName() : null)
                        .batchId(i.getBatch() != null ? i.getBatch().getId() : null)
                        .batchNumber(i.getBatch() != null ? i.getBatch().getBatchNumber() : null)
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .costPrice(i.getCostPrice())
                        .subtotal(i.getSubtotal())
                        .returnedQuantity(i.getReturnedQuantity())
                        .expiryDate(i.getBatch() != null ? i.getBatch().getExpiryDate() : null)
                        .build())
                .collect(Collectors.toList());

        return MedicineDTO.SaleResponseDTO.builder()
                .id(sale.getId())
                .invoiceNumber(sale.getInvoiceNumber())
                .saleDate(sale.getSaleDate())
                .totalAmount(sale.getTotalAmount())
                .customerName(sale.getCustomerName())
                .soldBy(sale.getSoldBy())
                .status(sale.getStatus())
                .items(itemDTOs)
                .build();
    }
}
