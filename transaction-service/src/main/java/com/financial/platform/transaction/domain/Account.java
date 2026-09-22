package com.financial.platform.transaction.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Account(
    UUID id,
    String accountNumber,
    BigDecimal balance,
    String currency,
    String status,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    public boolean hasSufficientBalance(BigDecimal amount) {
        return balance != null && balance.compareTo(amount) >= 0;
    }
}
