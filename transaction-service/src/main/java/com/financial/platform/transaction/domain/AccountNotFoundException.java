package com.financial.platform.transaction.domain;

import java.util.UUID;

public class AccountNotFoundException extends TransferBusinessException {
    public AccountNotFoundException(UUID accountId) {
        super("No se encontró la cuenta con ID: " + accountId);
    }
}
