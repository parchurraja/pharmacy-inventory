package com.pharmacy.inventory.exception;

public class BatchNotFoundException extends RuntimeException {
    public BatchNotFoundException(Long batchId) {
        super("Batch not found with ID: " + batchId);
    }

    public BatchNotFoundException(String batchNumber) {
        super("Batch not found with number: " + batchNumber);
    }
}
