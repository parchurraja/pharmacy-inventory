package com.pharmacy.inventory.exception;

public class MedicineNotFoundException extends RuntimeException {
    public MedicineNotFoundException(String medicineName) {
        super("Medicine not found: " + medicineName);
    }
}
