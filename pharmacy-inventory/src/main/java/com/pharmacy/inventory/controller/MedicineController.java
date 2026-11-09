package com.pharmacy.inventory.controller;

import com.pharmacy.inventory.dto.MedicineDTO;
import com.pharmacy.inventory.dto.PageResponse;
import com.pharmacy.inventory.service.MedicineService;
import com.pharmacy.inventory.service.PharmacyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/medicines")
@RequiredArgsConstructor
public class MedicineController {

    private final MedicineService medicineService;
    private final PharmacyService pharmacyService;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. MEDICINE CRUD (PAGEABLE & SORTED)
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<MedicineDTO.ApiResponse<MedicineDTO.MedicineResponse>> createMedicine(
            @Valid @RequestBody MedicineDTO.MedicineCreateRequest request) {
        MedicineDTO.MedicineResponse response = medicineService.createMedicine(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(MedicineDTO.ApiResponse.success("Medicine created successfully", response));
    }

    @GetMapping
    public ResponseEntity<MedicineDTO.ApiResponse<PageResponse<MedicineDTO.MedicineResponse>>> getAllMedicines(
            @PageableDefault(page = 0, size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active) {

        PageResponse<MedicineDTO.MedicineResponse> response = medicineService.getAllMedicines(pageable, search, active);
        return ResponseEntity.ok(MedicineDTO.ApiResponse.success(
                String.format("Fetched %d medicines (page %d of %d)",
                        response.getContent().size(), response.getPageNumber() + 1, response.getTotalPages()),
                response
        ));
    }

    @GetMapping("/{id:[0-9]+}")
    public ResponseEntity<MedicineDTO.ApiResponse<MedicineDTO.MedicineResponse>> getMedicineById(
            @PathVariable Long id) {
        MedicineDTO.MedicineResponse response = medicineService.getMedicineById(id);
        return ResponseEntity.ok(MedicineDTO.ApiResponse.success("Medicine found", response));
    }

    @PutMapping("/{id:[0-9]+}")
    public ResponseEntity<MedicineDTO.ApiResponse<MedicineDTO.MedicineResponse>> updateMedicine(
            @PathVariable Long id,
            @Valid @RequestBody MedicineDTO.MedicineUpdateRequest request) {
        MedicineDTO.MedicineResponse response = medicineService.updateMedicine(id, request);
        return ResponseEntity.ok(MedicineDTO.ApiResponse.success("Medicine updated successfully", response));
    }

    @PatchMapping("/{id:[0-9]+}/deactivate")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<MedicineDTO.ApiResponse<MedicineDTO.MedicineResponse>> deactivateMedicine(
            @PathVariable Long id) {
        MedicineDTO.MedicineResponse response = medicineService.deactivateMedicine(id);
        return ResponseEntity.ok(MedicineDTO.ApiResponse.success("Medicine deactivated successfully", response));
    }

    @PatchMapping("/{id:[0-9]+}/activate")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<MedicineDTO.ApiResponse<MedicineDTO.MedicineResponse>> activateMedicine(
            @PathVariable Long id) {
        MedicineDTO.MedicineResponse response = medicineService.activateMedicine(id);
        return ResponseEntity.ok(MedicineDTO.ApiResponse.success("Medicine reactivated successfully", response));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. BACKWARD COMPATIBLE & LEGACY ENDPOINTS
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/batch")
    public ResponseEntity<MedicineDTO.ApiResponse<MedicineDTO.MedicineStockResponse>> addBatch(
            @Valid @RequestBody MedicineDTO.AddBatchRequest request) {
        MedicineDTO.MedicineStockResponse response = pharmacyService.addBatch(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(MedicineDTO.ApiResponse.success("Batch added successfully for " + request.getMedicineName(), response));
    }

    @PostMapping("/sell")
    public ResponseEntity<MedicineDTO.ApiResponse<MedicineDTO.SellResponse>> sellMedicine(
            @Valid @RequestBody MedicineDTO.SellRequest request) {
        MedicineDTO.SellResponse response = pharmacyService.sellMedicine(request);
        return ResponseEntity.ok(MedicineDTO.ApiResponse.success(response.getMessage(), response));
    }

    @GetMapping("/expiring-soon")
    public ResponseEntity<MedicineDTO.ApiResponse<List<MedicineDTO.ExpiringSoonResponse>>> getExpiringSoon(
            @RequestParam(defaultValue = "7") int days) {
        List<MedicineDTO.ExpiringSoonResponse> expiringSoon = pharmacyService.getExpiringSoon(days);
        return ResponseEntity.ok(MedicineDTO.ApiResponse.success(
                expiringSoon.size() + " batch(es) expiring in next " + days + " day(s)",
                expiringSoon
        ));
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<MedicineDTO.ApiResponse<MedicineDTO.MedicineStockResponse>> getMedicineByName(
            @PathVariable String name) {
        MedicineDTO.MedicineStockResponse response = pharmacyService.getMedicineByName(name);
        return ResponseEntity.ok(MedicineDTO.ApiResponse.success("Medicine found", response));
    }
}
