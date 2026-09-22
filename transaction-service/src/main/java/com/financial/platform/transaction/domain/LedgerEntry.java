package com.financial.platform.transaction.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LedgerEntry(
    UUID id,
    UUID transactionId,
    UUID accountId,
    String entryType, // "DEBIT" or "CREDIT"
    BigDecimal amount,
    OffsetDateTime createdAt
) {}
