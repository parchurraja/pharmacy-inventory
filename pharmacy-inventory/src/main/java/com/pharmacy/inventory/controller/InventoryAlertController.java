package com.pharmacy.inventory.controller;

import com.pharmacy.inventory.dto.MedicineDTO;
import com.pharmacy.inventory.service.InventoryAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryAlertController {

    private final InventoryAlertService alertService;

    @GetMapping("/alerts/low-stock")
    public ResponseEntity<MedicineDTO.ApiResponse<List<MedicineDTO.LowStockAlertResponse>>> getLowStockAlerts() {
        List<MedicineDTO.LowStockAlertResponse> alerts = alertService.getLowStockAlerts();
        return ResponseEntity.ok(MedicineDTO.ApiResponse.success(
                String.format("Found %d medicine(s) with low or critical stock", alerts.size()),
                alerts
        ));
    }

    @GetMapping("/alerts/expiring")
    public ResponseEntity<MedicineDTO.ApiResponse<List<MedicineDTO.ExpiringSoonResponse>>> getExpiringBatches(
            @RequestParam(defaultValue = "30") int days) {

        List<MedicineDTO.ExpiringSoonResponse> batches = alertService.getExpiringBatches(days);
        return ResponseEntity.ok(MedicineDTO.ApiResponse.success(
                String.format("Found %d batch(es) expiring within next %d days", batches.size(), days),
                batches
        ));
    }

    @GetMapping({"/expired", "/alerts/expired"})
    public ResponseEntity<MedicineDTO.ApiResponse<List<MedicineDTO.BatchDetailResponse>>> getExpiredBatches() {
        List<MedicineDTO.BatchDetailResponse> expiredBatches = alertService.getExpiredBatches();
        return ResponseEntity.ok(MedicineDTO.ApiResponse.success(
                String.format("Found %d expired batch(es) requiring disposal", expiredBatches.size()),
                expiredBatches
        ));
    }
}
