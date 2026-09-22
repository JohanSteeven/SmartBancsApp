package com.financial.platform.transaction.domain;

public class SameAccountTransferException extends TransferBusinessException {
    public SameAccountTransferException(String message) {
        super(message);
    }
}
