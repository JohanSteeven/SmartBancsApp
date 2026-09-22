package com.financial.platform.transaction.domain;

public class AccountInactiveException extends TransferBusinessException {
    public AccountInactiveException(String message) {
        super(message);
    }
}
