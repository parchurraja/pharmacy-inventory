package com.pharmacy.inventory.exception;

public class InvalidSaleReturnException extends RuntimeException {
    public InvalidSaleReturnException(String message) {
        super(message);
    }
}
