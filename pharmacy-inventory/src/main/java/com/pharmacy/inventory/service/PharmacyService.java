package com.pharmacy.inventory.service;

import com.pharmacy.inventory.dto.MedicineDTO;
import com.pharmacy.inventory.entity.Medicine;
import com.pharmacy.inventory.entity.MedicineBatch;
import com.pharmacy.inventory.exception.MedicineNotFoundException;
import com.pharmacy.inventory.repository.MedicineBatchRepository;
import com.pharmacy.inventory.repository.MedicineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * High-level orchestration service for backward-compatible operations and stock summary queries.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PharmacyService {

    private final MedicineRepository medicineRepository;
    private final MedicineBatchRepository batchRepository;
    private final SaleService saleService;
    private final BatchService batchService;
    private final InventoryAlertService alertService;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. ADD MEDICINE BATCH (BACKWARD COMPATIBLE)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public MedicineDTO.MedicineStockResponse addBatch(MedicineDTO.AddBatchRequest request) {
        log.info("Adding batch for medicine: {}", request.getMedicineName());

        batchService.addBatch(MedicineDTO.BatchCreateRequest.builder()
                .medicineName(request.getMedicineName())
                .batchNumber(request.getBatchNumber())
                .quantity(request.getQuantity())
                .expiryDate(request.getExpiryDate())
                .costPrice(request.getCostPrice())
                .sellingPrice(request.getSellingPrice())
                .build());

        Medicine medicine = medicineRepository.findByNameIgnoreCase(request.getMedicineName())
                .orElseThrow(() -> new MedicineNotFoundException(request.getMedicineName()));

        return buildStockResponse(medicine);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. VIEW ALL MEDICINES WITH BATCHES
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MedicineDTO.MedicineStockResponse> getAllMedicines() {
        log.info("Fetching all medicines with batch details");
        return medicineRepository.findAll()
                .stream()
                .map(this::buildStockResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MedicineDTO.MedicineStockResponse getMedicineByName(String name) {
        Medicine medicine = medicineRepository
                .findByNameIgnoreCase(name)
                .orElseThrow(() -> new MedicineNotFoundException(name));
        return buildStockResponse(medicine);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. SELL MEDICINE — DELEGATES TO CONCURRENCY-SAFE FEFO SALE SERVICE
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public MedicineDTO.SellResponse sellMedicine(MedicineDTO.SellRequest request) {
        log.info("Selling {} units of {} via FEFO", request.getQuantity(), request.getMedicineName());

        MedicineDTO.SaleCreateRequest saleRequest = MedicineDTO.SaleCreateRequest.builder()
                .customerName(request.getCustomerName() != null ? request.getCustomerName() : "Walk-in Customer")
                .items(Collections.singletonList(
                        MedicineDTO.SaleItemRequest.builder()
                                .medicineName(request.getMedicineName())
                                .quantity(request.getQuantity())
                                .build()
                ))
                .build();

        MedicineDTO.SaleResponseDTO saleResponse = saleService.processSale(saleRequest, "system");

        List<MedicineDTO.BatchSoldDetail> batchesConsumed = saleResponse.getItems().stream()
                .map(item -> MedicineDTO.BatchSoldDetail.builder()
                        .batchNumber(item.getBatchNumber())
                        .quantityTakenFromThisBatch(item.getQuantity())
                        .expiryDate(item.getExpiryDate())
                        .unitPrice(item.getUnitPrice())
                        .subtotal(item.getSubtotal())
                        .build())
                .collect(Collectors.toList());

        return MedicineDTO.SellResponse.builder()
                .invoiceNumber(saleResponse.getInvoiceNumber())
                .medicineName(request.getMedicineName())
                .quantitySold(request.getQuantity())
                .totalAmount(saleResponse.getTotalAmount())
                .message(String.format("Successfully sold %d units of %s using FEFO (Earliest Expiry First)",
                        request.getQuantity(), request.getMedicineName()))
                .batchesConsumed(batchesConsumed)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. GET EXPIRING SOON MEDICINES (DELEGATES TO ALERT SERVICE)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MedicineDTO.ExpiringSoonResponse> getExpiringSoon(int days) {
        return alertService.getExpiringBatches(days);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPER: Build Stock Response
    // ─────────────────────────────────────────────────────────────────────────

    public MedicineDTO.MedicineStockResponse buildStockResponse(Medicine medicine) {
        List<MedicineDTO.BatchResponse> batchResponses = medicine.getBatches().stream()
                .map(b -> MedicineDTO.BatchResponse.builder()
                        .batchId(b.getId())
                        .batchNumber(b.getBatchNumber())
                        .quantity(b.getQuantity())
                        .expiryDate(b.getExpiryDate())
                        .expired(b.isExpired())
                        .sellingPrice(b.getSellingPrice())
                        .build())
                .collect(Collectors.toList());

        int totalQuantity = medicine.getBatches().stream()
                .filter(b -> b.isActive() && !b.isExpired())
                .mapToInt(MedicineBatch::getQuantity)
                .sum();

        return MedicineDTO.MedicineStockResponse.builder()
                .medicineId(medicine.getId())
                .medicineName(medicine.getName())
                .totalQuantity(totalQuantity)
                .batches(batchResponses)
                .build();
    }
}
