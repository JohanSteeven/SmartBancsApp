package com.financial.platform.transaction.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Transaction(
    UUID id,
    UUID sourceAccountId,
    UUID destinationAccountId,
    BigDecimal amount,
    String currency,
    TransactionStatus status,
    String idempotencyKey,
    OffsetDateTime createdAt
) {}
