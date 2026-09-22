package com.financial.platform.transaction.api;

import com.financial.platform.transaction.domain.Account;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountResponse(
    UUID id,
    String accountNumber,
    BigDecimal balance,
    String currency,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public static AccountResponse from(Account a) {
        return new AccountResponse(
            a.id(),
            a.accountNumber(),
            a.balance(),
            a.currency(),
            a.status(),
            a.createdAt(),
            a.updatedAt()
        );
    }
}
