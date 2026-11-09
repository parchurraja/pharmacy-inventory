package com.pharmacy.inventory.service;

import com.pharmacy.inventory.dto.MedicineDTO;
import com.pharmacy.inventory.dto.PageResponse;
import com.pharmacy.inventory.entity.Medicine;
import com.pharmacy.inventory.entity.MedicineBatch;
import com.pharmacy.inventory.exception.BatchNotFoundException;
import com.pharmacy.inventory.exception.MedicineNotFoundException;
import com.pharmacy.inventory.repository.MedicineBatchRepository;
import com.pharmacy.inventory.repository.MedicineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchService {

    private final MedicineBatchRepository batchRepository;
    private final MedicineRepository medicineRepository;

    @Transactional
    public MedicineDTO.BatchDetailResponse addBatch(MedicineDTO.BatchCreateRequest request) {
        log.info("Adding batch: {} for medicine ID: {} / Name: {}",
                request.getBatchNumber(), request.getMedicineId(), request.getMedicineName());

        Medicine medicine;
        if (request.getMedicineId() != null) {
            medicine = medicineRepository.findById(request.getMedicineId())
                    .orElseThrow(() -> new MedicineNotFoundException("ID: " + request.getMedicineId()));
        } else if (StringUtils.hasText(request.getMedicineName())) {
            medicine = medicineRepository.findByNameIgnoreCase(request.getMedicineName().trim())
                    .orElseGet(() -> {
                        Medicine newMedicine = Medicine.builder()
                                .name(request.getMedicineName().trim())
                                .lowStockThreshold(10)
                                .active(true)
                                .build();
                        return medicineRepository.save(newMedicine);
                    });
        } else {
            throw new IllegalArgumentException("Either medicineId or medicineName must be provided");
        }

        MedicineBatch batch = MedicineBatch.builder()
                .batchNumber(request.getBatchNumber().trim())
                .quantity(request.getQuantity())
                .expiryDate(request.getExpiryDate())
                .costPrice(request.getCostPrice())
                .sellingPrice(request.getSellingPrice())
                .active(true)
                .medicine(medicine)
                .build();

        MedicineBatch saved = batchRepository.save(batch);
        medicine.getBatches().add(saved);

        return mapToBatchDetailResponse(saved);
    }

    @Transactional
    public MedicineDTO.BatchDetailResponse updateBatch(Long id, MedicineDTO.BatchUpdateRequest request) {
        log.info("Updating batch id: {}", id);

        MedicineBatch batch = batchRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BatchNotFoundException(id));

        batch.setQuantity(request.getQuantity());
        batch.setExpiryDate(request.getExpiryDate());
        if (request.getCostPrice() != null) {
            batch.setCostPrice(request.getCostPrice());
        }
        if (request.getSellingPrice() != null) {
            batch.setSellingPrice(request.getSellingPrice());
        }
        if (request.getActive() != null) {
            batch.setActive(request.getActive());
        }

        MedicineBatch saved = batchRepository.save(batch);
        return mapToBatchDetailResponse(saved);
    }

    @Transactional(readOnly = true)
    public MedicineDTO.BatchDetailResponse getBatchById(Long id) {
        MedicineBatch batch = batchRepository.findById(id)
                .orElseThrow(() -> new BatchNotFoundException(id));
        return mapToBatchDetailResponse(batch);
    }

    @Transactional(readOnly = true)
    public PageResponse<MedicineDTO.BatchDetailResponse> getAllBatches(
            Pageable pageable,
            Long medicineId,
            Boolean active) {

        Page<MedicineBatch> page;

        if (medicineId != null) {
            page = batchRepository.findByMedicineId(medicineId, pageable);
        } else if (active != null) {
            page = batchRepository.findByActive(active, pageable);
        } else {
            page = batchRepository.findAll(pageable);
        }

        return PageResponse.from(page, this::mapToBatchDetailResponse);
    }

    public MedicineDTO.BatchDetailResponse mapToBatchDetailResponse(MedicineBatch batch) {
        return MedicineDTO.BatchDetailResponse.builder()
                .id(batch.getId())
                .batchNumber(batch.getBatchNumber())
                .quantity(batch.getQuantity())
                .expiryDate(batch.getExpiryDate())
                .costPrice(batch.getCostPrice())
                .sellingPrice(batch.getSellingPrice())
                .active(batch.isActive())
                .expired(batch.isExpired())
                .medicineId(batch.getMedicine() != null ? batch.getMedicine().getId() : null)
                .medicineName(batch.getMedicine() != null ? batch.getMedicine().getName() : null)
                .build();
    }
}
