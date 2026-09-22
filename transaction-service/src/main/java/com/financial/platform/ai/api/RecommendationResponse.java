package com.financial.platform.ai.api;

import com.financial.platform.ai.infrastructure.RecommendationJdbcRepository.RecommendationEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record RecommendationResponse(
    UUID id,
    UUID transactionId,
    BigDecimal riskScore,
    String recommendation,
    String modelVersion,
    OffsetDateTime createdAt
) {
    public static RecommendationResponse from(RecommendationEntity entity) {
        return new RecommendationResponse(
            entity.id(),
            entity.transactionId(),
            entity.riskScore(),
            entity.recommendation(),
            entity.modelVersion(),
            entity.createdAt()
        );
    }
}
