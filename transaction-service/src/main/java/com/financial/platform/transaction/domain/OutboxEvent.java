package com.financial.platform.transaction.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OutboxEvent(
    UUID id,
    String aggregateType,
    UUID aggregateId,
    String eventType,
    String payload,
    String status,
    int retryCount,
    OffsetDateTime createdAt,
    OffsetDateTime publishedAt
) {}
