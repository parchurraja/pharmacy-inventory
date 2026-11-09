package com.pharmacy.inventory.dto;

import com.pharmacy.inventory.entity.AdjustmentType;
import com.pharmacy.inventory.entity.SaleStatus;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class MedicineDTO {

    // ─── Legacy / Compatibility Request DTOs ──────────────────────────

    /** Request: Add a new medicine batch (Legacy support) */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AddBatchRequest {
        @NotBlank(message = "Medicine name is required")
        private String medicineName;

        @NotBlank(message = "Batch number is required")
        private String batchNumber;

        @Min(value = 1, message = "Quantity must be at least 1")
        private int quantity;

        @NotNull(message = "Expiry date is required")
        @Future(message = "Expiry date must be in the future")
        private LocalDate expiryDate;

        private BigDecimal costPrice;
        private BigDecimal sellingPrice;
    }

    /** Request: Sell medicine (Legacy & Single item support) */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SellRequest {
        @NotBlank(message = "Medicine name is required")
        private String medicineName;

        @Min(value = 1, message = "Quantity must be at least 1")
        private int quantity;

        private String customerName;
    }

    // ─── New Medicine CRUD DTOs ──────────────────────────────────────

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MedicineCreateRequest {
        @NotBlank(message = "Medicine name is required")
        private String name;

        private String description;
        private String category;

        @Min(value = 0, message = "Low stock threshold cannot be negative")
        @Builder.Default
        private int lowStockThreshold = 10;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MedicineUpdateRequest {
        @NotBlank(message = "Medicine name is required")
        private String name;

        private String description;
        private String category;

        @Min(value = 0, message = "Low stock threshold cannot be negative")
        private int lowStockThreshold;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MedicineResponse {
        private Long id;
        private String name;
        private String description;
        private String category;
        private int lowStockThreshold;
        private boolean active;
        private int totalStock;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // ─── Batch DTOs ──────────────────────────────────────────────────

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BatchCreateRequest {
        private Long medicineId;
        private String medicineName;

        @NotBlank(message = "Batch number is required")
        private String batchNumber;

        @Min(value = 1, message = "Quantity must be at least 1")
        private int quantity;

        @NotNull(message = "Expiry date is required")
        private LocalDate expiryDate;

        private BigDecimal costPrice;
        private BigDecimal sellingPrice;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BatchUpdateRequest {
        @Min(value = 0, message = "Quantity cannot be negative")
        private int quantity;

        @NotNull(message = "Expiry date is required")
        private LocalDate expiryDate;

        private BigDecimal costPrice;
        private BigDecimal sellingPrice;
        private Boolean active;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BatchDetailResponse {
        private Long id;
        private String batchNumber;
        private int quantity;
        private LocalDate expiryDate;
        private BigDecimal costPrice;
        private BigDecimal sellingPrice;
        private boolean active;
        private boolean expired;
        private Long medicineId;
        private String medicineName;
    }

    // ─── Sales & FEFO DTOs ───────────────────────────────────────────

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SaleCreateRequest {
        private String customerName;

        @NotEmpty(message = "Sale must contain at least one item")
        private List<SaleItemRequest> items;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SaleItemRequest {
        private Long medicineId;
        private String medicineName;

        @Min(value = 1, message = "Quantity must be at least 1")
        private int quantity;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SaleItemResponseDTO {
        private Long id;
        private Long medicineId;
        private String medicineName;
        private Long batchId;
        private String batchNumber;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal costPrice;
        private BigDecimal subtotal;
        private int returnedQuantity;
        private LocalDate expiryDate;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SaleResponseDTO {
        private Long id;
        private String invoiceNumber;
        private LocalDateTime saleDate;
        private BigDecimal totalAmount;
        private String customerName;
        private String soldBy;
        private SaleStatus status;
        private List<SaleItemResponseDTO> items;
    }

    // ─── Sales Return DTOs ───────────────────────────────────────────

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReturnItemRequest {
        @NotNull(message = "Sale item ID is required")
        private Long saleItemId;

        @Min(value = 1, message = "Return quantity must be at least 1")
        private int quantity;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SaleReturnRequest {
        @NotEmpty(message = "At least one item must be returned")
        private List<ReturnItemRequest> items;

        private String reason;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SaleReturnResponse {
        private String invoiceNumber;
        private SaleStatus status;
        private int totalQuantityReturned;
        private BigDecimal refundAmount;
        private String message;
    }

    // ─── Stock Adjustment DTOs ───────────────────────────────────────

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StockAdjustmentRequest {
        @NotNull(message = "Batch ID is required")
        private Long batchId;

        @NotNull(message = "Adjustment type is required")
        private AdjustmentType adjustmentType;

        @NotNull(message = "Quantity adjusted is required")
        private int quantityAdjusted;

        @NotBlank(message = "Reason is required")
        private String reason;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StockAdjustmentResponse {
        private Long id;
        private Long batchId;
        private String batchNumber;
        private String medicineName;
        private AdjustmentType adjustmentType;
        private int quantityAdjusted;
        private String reason;
        private String adjustedBy;
        private LocalDateTime adjustedAt;
    }

    // ─── Alert DTOs ──────────────────────────────────────────────────

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LowStockAlertResponse {
        private Long medicineId;
        private String medicineName;
        private String category;
        private int currentStock;
        private int lowStockThreshold;
        private String alertLevel; // WARNING, CRITICAL
    }

    // ─── Legacy Response DTOs (Preserved for compatibility) ──────────

    /** Response: Single batch details */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BatchResponse {
        private Long batchId;
        private String batchNumber;
        private int quantity;
        private LocalDate expiryDate;
        private boolean expired;
        private BigDecimal sellingPrice;
    }

    /** Response: Medicine with all its batches */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MedicineStockResponse {
        private Long medicineId;
        private String medicineName;
        private int totalQuantity;
        private List<BatchResponse> batches;
    }

    /** Response: Sell result showing which batches were consumed (FEFO trace) */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SellResponse {
        private String invoiceNumber;
        private String medicineName;
        private int quantitySold;
        private BigDecimal totalAmount;
        private String message;
        private List<BatchSoldDetail> batchesConsumed;
    }

    /** Detail of each batch consumed during a sale (FEFO trace) */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BatchSoldDetail {
        private String batchNumber;
        private int quantityTakenFromThisBatch;
        private LocalDate expiryDate;
        private BigDecimal unitPrice;
        private BigDecimal subtotal;
    }

    /** Response: Expiring-soon medicine batch */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExpiringSoonResponse {
        private Long batchId;
        private String medicineName;
        private String batchNumber;
        private int quantity;
        private LocalDate expiryDate;
        private long daysUntilExpiry;
    }

    /** Generic API response wrapper */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;
        private Object errors;

        public static <T> ApiResponse<T> success(String message, T data) {
            return ApiResponse.<T>builder()
                    .success(true)
                    .message(message)
                    .data(data)
                    .build();
        }

        public static <T> ApiResponse<T> error(String message) {
            return ApiResponse.<T>builder()
                    .success(false)
                    .message(message)
                    .build();
        }

        public static <T> ApiResponse<T> error(String message, Object errors) {
            return ApiResponse.<T>builder()
                    .success(false)
                    .message(message)
                    .errors(errors)
                    .build();
        }
    }
}
