package com.financial.platform.transaction.api;

import com.financial.platform.transaction.domain.Transaction;
import com.financial.platform.transaction.domain.TransactionStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransferResponse(
    UUID transactionId,
    UUID sourceAccountId,
    UUID destinationAccountId,
    BigDecimal amount,
    String currency,
    TransactionStatus status,
    String idempotencyKey,
    boolean isIdempotentResponse,
    OffsetDateTime createdAt
) {
    public static TransferResponse from(Transaction t, boolean isIdempotent) {
        return new TransferResponse(
            t.id(),
            t.sourceAccountId(),
            t.destinationAccountId(),
            t.amount(),
            t.currency(),
            t.status(),
            t.idempotencyKey(),
            isIdempotent,
            t.createdAt()
        );
    }
}
