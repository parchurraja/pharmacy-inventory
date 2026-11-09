package com.pharmacy.inventory.controller;

import com.pharmacy.inventory.dto.MedicineDTO;
import com.pharmacy.inventory.dto.PageResponse;
import com.pharmacy.inventory.service.BatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/batches")
@RequiredArgsConstructor
public class BatchController {

    private final BatchService batchService;

    @PostMapping
    public ResponseEntity<MedicineDTO.ApiResponse<MedicineDTO.BatchDetailResponse>> addBatch(
            @Valid @RequestBody MedicineDTO.BatchCreateRequest request) {
        MedicineDTO.BatchDetailResponse response = batchService.addBatch(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(MedicineDTO.ApiResponse.success("Batch created successfully", response));
    }

    @GetMapping
    public ResponseEntity<MedicineDTO.ApiResponse<PageResponse<MedicineDTO.BatchDetailResponse>>> getAllBatches(
            @PageableDefault(page = 0, size = 10, sort = "expiryDate", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) Long medicineId,
            @RequestParam(required = false) Boolean active) {

        PageResponse<MedicineDTO.BatchDetailResponse> response = batchService.getAllBatches(pageable, medicineId, active);
        return ResponseEntity.ok(MedicineDTO.ApiResponse.success(
                String.format("Fetched %d batches (page %d of %d)",
                        response.getContent().size(), response.getPageNumber() + 1, response.getTotalPages()),
                response
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MedicineDTO.ApiResponse<MedicineDTO.BatchDetailResponse>> getBatchById(
            @PathVariable Long id) {
        MedicineDTO.BatchDetailResponse response = batchService.getBatchById(id);
        return ResponseEntity.ok(MedicineDTO.ApiResponse.success("Batch found", response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MedicineDTO.ApiResponse<MedicineDTO.BatchDetailResponse>> updateBatch(
            @PathVariable Long id,
            @Valid @RequestBody MedicineDTO.BatchUpdateRequest request) {
        MedicineDTO.BatchDetailResponse response = batchService.updateBatch(id, request);
        return ResponseEntity.ok(MedicineDTO.ApiResponse.success("Batch updated successfully", response));
    }
}
