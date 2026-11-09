package com.pharmacy.inventory.service;

import com.pharmacy.inventory.dto.MedicineDTO;
import com.pharmacy.inventory.entity.Medicine;
import com.pharmacy.inventory.entity.MedicineBatch;
import com.pharmacy.inventory.repository.MedicineBatchRepository;
import com.pharmacy.inventory.repository.MedicineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryAlertService {

    private final MedicineRepository medicineRepository;
    private final MedicineBatchRepository batchRepository;

    @Transactional(readOnly = true)
    public List<MedicineDTO.LowStockAlertResponse> getLowStockAlerts() {
        log.info("Calculating low stock alerts");

        List<Medicine> allMedicines = medicineRepository.findAll();
        List<MedicineDTO.LowStockAlertResponse> alerts = new ArrayList<>();

        for (Medicine medicine : allMedicines) {
            if (!medicine.isActive()) continue;

            int currentStock = medicine.getBatches().stream()
                    .filter(b -> b.isActive() && !b.isExpired())
                    .mapToInt(MedicineBatch::getQuantity)
                    .sum();

            if (currentStock <= medicine.getLowStockThreshold()) {
                String level = currentStock == 0 ? "CRITICAL (OUT OF STOCK)" : "WARNING (LOW STOCK)";

                alerts.add(MedicineDTO.LowStockAlertResponse.builder()
                        .medicineId(medicine.getId())
                        .medicineName(medicine.getName())
                        .category(medicine.getCategory())
                        .currentStock(currentStock)
                        .lowStockThreshold(medicine.getLowStockThreshold())
                        .alertLevel(level)
                        .build());
            }
        }

        return alerts;
    }

    @Transactional(readOnly = true)
    public List<MedicineDTO.ExpiringSoonResponse> getExpiringBatches(int days) {
        log.info("Querying batches expiring within {} days", days);

        LocalDate today = LocalDate.now();
        LocalDate targetDate = today.plusDays(days);

        List<MedicineBatch> batches = batchRepository.findBatchesExpiringSoon(today, targetDate);

        return batches.stream()
                .map(batch -> MedicineDTO.ExpiringSoonResponse.builder()
                        .batchId(batch.getId())
                        .medicineName(batch.getMedicine() != null ? batch.getMedicine().getName() : "Unknown")
                        .batchNumber(batch.getBatchNumber())
                        .quantity(batch.getQuantity())
                        .expiryDate(batch.getExpiryDate())
                        .daysUntilExpiry(ChronoUnit.DAYS.between(today, batch.getExpiryDate()))
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MedicineDTO.BatchDetailResponse> getExpiredBatches() {
        log.info("Querying currently expired stock batches");

        LocalDate today = LocalDate.now();
        List<MedicineBatch> expiredBatches = batchRepository.findExpiredBatches(today);

        return expiredBatches.stream()
                .map(batch -> MedicineDTO.BatchDetailResponse.builder()
                        .id(batch.getId())
                        .batchNumber(batch.getBatchNumber())
                        .quantity(batch.getQuantity())
                        .expiryDate(batch.getExpiryDate())
                        .costPrice(batch.getCostPrice())
                        .sellingPrice(batch.getSellingPrice())
                        .active(batch.isActive())
                        .expired(true)
                        .medicineId(batch.getMedicine() != null ? batch.getMedicine().getId() : null)
                        .medicineName(batch.getMedicine() != null ? batch.getMedicine().getName() : null)
                        .build())
                .collect(Collectors.toList());
    }
}
