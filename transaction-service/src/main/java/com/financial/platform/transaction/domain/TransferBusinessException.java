package com.financial.platform.transaction.domain;

public class TransferBusinessException extends RuntimeException {
    public TransferBusinessException(String message) {
        super(message);
    }

    public TransferBusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
