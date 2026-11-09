package com.pharmacy.inventory.exception;

public class ExpiredMedicineException extends RuntimeException {
    public ExpiredMedicineException(String medicineName) {
        super("Cannot sell '" + medicineName + "': all available stock is expired.");
    }
}
