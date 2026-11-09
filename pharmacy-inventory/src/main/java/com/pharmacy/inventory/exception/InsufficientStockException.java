package com.pharmacy.inventory.exception;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String medicineName, int requested, int available) {
        super(String.format(
            "Insufficient stock for '%s'. Requested: %d, Available (non-expired): %d",
            medicineName, requested, available
        ));
    }
}
