package com.pharmacy.inventory.service;

import com.pharmacy.inventory.dto.MedicineDTO;
import com.pharmacy.inventory.dto.PageResponse;
import com.pharmacy.inventory.entity.Medicine;
import com.pharmacy.inventory.entity.MedicineBatch;
import com.pharmacy.inventory.exception.DuplicateResourceException;
import com.pharmacy.inventory.exception.MedicineNotFoundException;
import com.pharmacy.inventory.repository.MedicineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class MedicineService {

    private final MedicineRepository medicineRepository;

    @Transactional
    public MedicineDTO.MedicineResponse createMedicine(MedicineDTO.MedicineCreateRequest request) {
        log.info("Creating medicine: {}", request.getName());

        if (medicineRepository.existsByNameIgnoreCase(request.getName())) {
            throw new DuplicateResourceException("Medicine already exists with name: " + request.getName());
        }

        Medicine medicine = Medicine.builder()
                .name(request.getName().trim())
                .description(request.getDescription())
                .category(request.getCategory())
                .lowStockThreshold(request.getLowStockThreshold() > 0 ? request.getLowStockThreshold() : 10)
                .active(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Medicine saved = medicineRepository.save(medicine);
        return mapToMedicineResponse(saved);
    }

    @Transactional
    public MedicineDTO.MedicineResponse updateMedicine(Long id, MedicineDTO.MedicineUpdateRequest request) {
        log.info("Updating medicine id: {}", id);

        Medicine medicine = medicineRepository.findById(id)
                .orElseThrow(() -> new MedicineNotFoundException("ID: " + id));

        if (medicineRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), id)) {
            throw new DuplicateResourceException("Another medicine already exists with name: " + request.getName());
        }

        medicine.setName(request.getName().trim());
        medicine.setDescription(request.getDescription());
        medicine.setCategory(request.getCategory());
        medicine.setLowStockThreshold(request.getLowStockThreshold());
        medicine.setUpdatedAt(LocalDateTime.now());

        Medicine saved = medicineRepository.save(medicine);
        return mapToMedicineResponse(saved);
    }

    @Transactional(readOnly = true)
    public MedicineDTO.MedicineResponse getMedicineById(Long id) {
        Medicine medicine = medicineRepository.findById(id)
                .orElseThrow(() -> new MedicineNotFoundException("ID: " + id));
        return mapToMedicineResponse(medicine);
    }

    @Transactional(readOnly = true)
    public MedicineDTO.MedicineResponse getMedicineByName(String name) {
        Medicine medicine = medicineRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> new MedicineNotFoundException(name));
        return mapToMedicineResponse(medicine);
    }

    @Transactional(readOnly = true)
    public PageResponse<MedicineDTO.MedicineResponse> getAllMedicines(
            Pageable pageable,
            String search,
            Boolean active) {

        log.info("Fetching medicines page: {}, search: '{}', active: {}", pageable.getPageNumber(), search, active);

        Page<Medicine> page;

        if (StringUtils.hasText(search)) {
            if (active != null) {
                page = medicineRepository.findByNameContainingIgnoreCaseAndActive(search.trim(), active, pageable);
            } else {
                page = medicineRepository.findByNameContainingIgnoreCase(search.trim(), pageable);
            }
        } else {
            if (active != null) {
                page = medicineRepository.findByActive(active, pageable);
            } else {
                page = medicineRepository.findAll(pageable);
            }
        }

        return PageResponse.from(page, this::mapToMedicineResponse);
    }

    @Transactional
    public MedicineDTO.MedicineResponse deactivateMedicine(Long id) {
        log.info("Deactivating medicine id: {}", id);
        Medicine medicine = medicineRepository.findById(id)
                .orElseThrow(() -> new MedicineNotFoundException("ID: " + id));

        medicine.setActive(false);
        medicine.setUpdatedAt(LocalDateTime.now());
        return mapToMedicineResponse(medicineRepository.save(medicine));
    }

    @Transactional
    public MedicineDTO.MedicineResponse activateMedicine(Long id) {
        log.info("Reactivating medicine id: {}", id);
        Medicine medicine = medicineRepository.findById(id)
                .orElseThrow(() -> new MedicineNotFoundException("ID: " + id));

        medicine.setActive(true);
        medicine.setUpdatedAt(LocalDateTime.now());
        return mapToMedicineResponse(medicineRepository.save(medicine));
    }

    public MedicineDTO.MedicineResponse mapToMedicineResponse(Medicine medicine) {
        int totalValidStock = medicine.getBatches() == null ? 0 : medicine.getBatches().stream()
                .filter(b -> b.isActive() && !b.isExpired())
                .mapToInt(MedicineBatch::getQuantity)
                .sum();

        return MedicineDTO.MedicineResponse.builder()
                .id(medicine.getId())
                .name(medicine.getName())
                .description(medicine.getDescription())
                .category(medicine.getCategory())
                .lowStockThreshold(medicine.getLowStockThreshold())
                .active(medicine.isActive())
                .totalStock(totalValidStock)
                .createdAt(medicine.getCreatedAt())
                .updatedAt(medicine.getUpdatedAt())
                .build();
    }
}
