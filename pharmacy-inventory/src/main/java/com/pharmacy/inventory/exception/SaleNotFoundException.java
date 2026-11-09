package com.pharmacy.inventory.exception;

public class SaleNotFoundException extends RuntimeException {
    public SaleNotFoundException(Long saleId) {
        super("Sale not found with ID: " + saleId);
    }

    public SaleNotFoundException(String invoiceNumber) {
        super("Sale not found with invoice number: " + invoiceNumber);
    }
}
