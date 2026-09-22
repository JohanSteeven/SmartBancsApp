package com.financial.platform.transaction.domain;

public class CurrencyMismatchException extends TransferBusinessException {
    public CurrencyMismatchException(String message) {
        super(message);
    }
}
