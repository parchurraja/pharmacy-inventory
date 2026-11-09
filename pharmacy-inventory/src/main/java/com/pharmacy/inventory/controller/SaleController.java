package com.pharmacy.inventory.controller;

import com.pharmacy.inventory.dto.MedicineDTO;
import com.pharmacy.inventory.dto.PageResponse;
import com.pharmacy.inventory.service.SaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;

    @PostMapping
    public ResponseEntity<MedicineDTO.ApiResponse<MedicineDTO.SaleResponseDTO>> createSale(
            @Valid @RequestBody MedicineDTO.SaleCreateRequest request,
            Authentication authentication) {

        String soldBy = (authentication != null) ? authentication.getName() : "pharmacist";
        MedicineDTO.SaleResponseDTO response = saleService.processSale(request, soldBy);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(MedicineDTO.ApiResponse.success(
                        "Sale completed successfully with FEFO batch allocation. Invoice: " + response.getInvoiceNumber(),
                        response
                ));
    }

    @GetMapping
    public ResponseEntity<MedicineDTO.ApiResponse<PageResponse<MedicineDTO.SaleResponseDTO>>> getSalesHistory(
            @PageableDefault(page = 0, size = 10, sort = "saleDate", direction = Sort.Direction.DESC) Pageable pageable) {

        PageResponse<MedicineDTO.SaleResponseDTO> response = saleService.getSalesHistory(pageable);
        return ResponseEntity.ok(MedicineDTO.ApiResponse.success(
                String.format("Fetched %d sales (page %d of %d)",
                        response.getContent().size(), response.getPageNumber() + 1, response.getTotalPages()),
                response
        ));
    }

    @GetMapping("/{id:[0-9]+}")
    public ResponseEntity<MedicineDTO.ApiResponse<MedicineDTO.SaleResponseDTO>> getSaleById(
            @PathVariable Long id) {
        MedicineDTO.SaleResponseDTO response = saleService.getSaleById(id);
        return ResponseEntity.ok(MedicineDTO.ApiResponse.success("Sale details found", response));
    }

    @GetMapping("/invoice/{invoiceNumber}")
    public ResponseEntity<MedicineDTO.ApiResponse<MedicineDTO.SaleResponseDTO>> getSaleByInvoice(
            @PathVariable String invoiceNumber) {
        MedicineDTO.SaleResponseDTO response = saleService.getSaleByInvoice(invoiceNumber);
        return ResponseEntity.ok(MedicineDTO.ApiResponse.success("Sale details found", response));
    }

    @PostMapping("/{id:[0-9]+}/return")
    public ResponseEntity<MedicineDTO.ApiResponse<MedicineDTO.SaleReturnResponse>> processSaleReturn(
            @PathVariable Long id,
            @Valid @RequestBody MedicineDTO.SaleReturnRequest request,
            Authentication authentication) {

        String adjustedBy = (authentication != null) ? authentication.getName() : "pharmacist";
        MedicineDTO.SaleReturnResponse response = saleService.processReturn(id, request, adjustedBy);

        return ResponseEntity.ok(MedicineDTO.ApiResponse.success(response.getMessage(), response));
    }
}
