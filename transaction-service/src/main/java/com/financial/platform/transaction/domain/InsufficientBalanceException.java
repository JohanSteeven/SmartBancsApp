package com.financial.platform.transaction.domain;

public class InsufficientBalanceException extends TransferBusinessException {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}
