package com.pharmacy.inventory.service;

import com.pharmacy.inventory.dto.MedicineDTO;
import com.pharmacy.inventory.dto.PageResponse;
import com.pharmacy.inventory.entity.AdjustmentType;
import com.pharmacy.inventory.entity.MedicineBatch;
import com.pharmacy.inventory.entity.StockAdjustment;
import com.pharmacy.inventory.exception.BatchNotFoundException;
import com.pharmacy.inventory.repository.MedicineBatchRepository;
import com.pharmacy.inventory.repository.StockAdjustmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockAdjustmentService {

    private final MedicineBatchRepository batchRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;

    @Transactional(rollbackFor = Exception.class)
    public MedicineDTO.StockAdjustmentResponse adjustStock(
            MedicineDTO.StockAdjustmentRequest request,
            String adjustedBy) {

        log.info("Adjusting stock for batch ID: {}, type: {}, quantity: {}, adjusted by: {}",
                request.getBatchId(), request.getAdjustmentType(), request.getQuantityAdjusted(), adjustedBy);

        MedicineBatch batch = batchRepository.findByIdForUpdate(request.getBatchId())
                .orElseThrow(() -> new BatchNotFoundException(request.getBatchId()));

        int newQuantity = batch.getQuantity() + request.getQuantityAdjusted();
        if (newQuantity < 0) {
            throw new IllegalArgumentException(String.format(
                    "Invalid adjustment: Cannot reduce batch quantity from %d by %d (results in negative stock %d)",
                    batch.getQuantity(), Math.abs(request.getQuantityAdjusted()), newQuantity));
        }

        batch.setQuantity(newQuantity);
        batchRepository.save(batch);

        StockAdjustment adjustment = StockAdjustment.builder()
                .batch(batch)
                .adjustmentType(request.getAdjustmentType())
                .quantityAdjusted(request.getQuantityAdjusted())
                .reason(request.getReason())
                .adjustedBy(adjustedBy)
                .adjustedAt(LocalDateTime.now())
                .build();

        StockAdjustment saved = stockAdjustmentRepository.save(adjustment);
        return mapToAdjustmentResponse(saved);
    }

    @Transactional(rollbackFor = Exception.class)
    public MedicineDTO.StockAdjustmentResponse disposeExpiredBatch(Long batchId, String adjustedBy) {
        log.info("Disposing expired batch ID: {}, adjusted by: {}", batchId, adjustedBy);

        MedicineBatch batch = batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new BatchNotFoundException(batchId));

        int quantityToRemove = batch.getQuantity();
        if (quantityToRemove <= 0) {
            throw new IllegalArgumentException("Batch already has zero stock to dispose: " + batch.getBatchNumber());
        }

        batch.setQuantity(0);
        batch.setActive(false);
        batchRepository.save(batch);

        StockAdjustment adjustment = StockAdjustment.builder()
                .batch(batch)
                .adjustmentType(AdjustmentType.EXPIRED_DISPOSAL)
                .quantityAdjusted(-quantityToRemove)
                .reason("Batch expired on " + batch.getExpiryDate() + " - safely disposed and written off")
                .adjustedBy(adjustedBy)
                .adjustedAt(LocalDateTime.now())
                .build();

        StockAdjustment saved = stockAdjustmentRepository.save(adjustment);
        return mapToAdjustmentResponse(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<MedicineDTO.StockAdjustmentResponse> getAdjustments(Pageable pageable) {
        Page<StockAdjustment> page = stockAdjustmentRepository.findAll(pageable);
        return PageResponse.from(page, this::mapToAdjustmentResponse);
    }

    public MedicineDTO.StockAdjustmentResponse mapToAdjustmentResponse(StockAdjustment adjustment) {
        MedicineBatch batch = adjustment.getBatch();
        String medicineName = (batch != null && batch.getMedicine() != null)
                ? batch.getMedicine().getName()
                : "Unknown";

        return MedicineDTO.StockAdjustmentResponse.builder()
                .id(adjustment.getId())
                .batchId(batch != null ? batch.getId() : null)
                .batchNumber(batch != null ? batch.getBatchNumber() : null)
                .medicineName(medicineName)
                .adjustmentType(adjustment.getAdjustmentType())
                .quantityAdjusted(adjustment.getQuantityAdjusted())
                .reason(adjustment.getReason())
                .adjustedBy(adjustment.getAdjustedBy())
                .adjustedAt(adjustment.getAdjustedAt())
                .build();
    }
}
