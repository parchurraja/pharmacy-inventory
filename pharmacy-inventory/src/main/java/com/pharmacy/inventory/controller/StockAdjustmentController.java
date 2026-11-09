package com.pharmacy.inventory.controller;

import com.pharmacy.inventory.dto.MedicineDTO;
import com.pharmacy.inventory.dto.PageResponse;
import com.pharmacy.inventory.service.StockAdjustmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class StockAdjustmentController {

    private final StockAdjustmentService stockAdjustmentService;

    @PostMapping("/adjustments")
    public ResponseEntity<MedicineDTO.ApiResponse<MedicineDTO.StockAdjustmentResponse>> adjustStock(
            @Valid @RequestBody MedicineDTO.StockAdjustmentRequest request,
            Authentication authentication) {

        String adjustedBy = (authentication != null) ? authentication.getName() : "pharmacist";
        MedicineDTO.StockAdjustmentResponse response = stockAdjustmentService.adjustStock(request, adjustedBy);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(MedicineDTO.ApiResponse.success("Stock adjusted successfully", response));
    }

    @PostMapping("/expired/{batchId:[0-9]+}/dispose")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<MedicineDTO.ApiResponse<MedicineDTO.StockAdjustmentResponse>> disposeExpiredBatch(
            @PathVariable Long batchId,
            Authentication authentication) {

        String adjustedBy = (authentication != null) ? authentication.getName() : "admin";
        MedicineDTO.StockAdjustmentResponse response = stockAdjustmentService.disposeExpiredBatch(batchId, adjustedBy);

        return ResponseEntity.ok(MedicineDTO.ApiResponse.success(
                "Expired batch successfully disposed and written off from inventory", response));
    }

    @GetMapping("/adjustments")
    public ResponseEntity<MedicineDTO.ApiResponse<PageResponse<MedicineDTO.StockAdjustmentResponse>>> getAdjustments(
            @PageableDefault(page = 0, size = 10, sort = "adjustedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        PageResponse<MedicineDTO.StockAdjustmentResponse> response = stockAdjustmentService.getAdjustments(pageable);
        return ResponseEntity.ok(MedicineDTO.ApiResponse.success(
                String.format("Fetched %d adjustments (page %d of %d)",
                        response.getContent().size(), response.getPageNumber() + 1, response.getTotalPages()),
                response
        ));
    }
}
