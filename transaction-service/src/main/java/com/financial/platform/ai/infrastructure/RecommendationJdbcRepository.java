package com.financial.platform.ai.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RecommendationJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public RecommendationJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record RecommendationEntity(
        UUID id,
        UUID transactionId,
        BigDecimal riskScore,
        String recommendation,
        String modelVersion,
        OffsetDateTime createdAt
    ) {}

    private final RowMapper<RecommendationEntity> rowMapper = (rs, rowNum) -> new RecommendationEntity(
        UUID.fromString(rs.getString("id")),
        UUID.fromString(rs.getString("transaction_id")),
        rs.getBigDecimal("risk_score"),
        rs.getString("recommendation"),
        rs.getString("model_version"),
        rs.getObject("created_at", OffsetDateTime.class)
    );

    public void save(UUID transactionId, BigDecimal riskScore, String recommendation, String modelVersion) {
        UUID id = UUID.randomUUID();
        String sql = """
            INSERT INTO recommendations (id, transaction_id, risk_score, recommendation, model_version, created_at)
            VALUES (?, ?, ?, ?, ?, NOW())
            """;
        jdbcTemplate.update(sql, id, transactionId, riskScore, recommendation, modelVersion);
    }

    public Optional<RecommendationEntity> findByTransactionId(UUID transactionId) {
        String sql = """
            SELECT id, transaction_id, risk_score, recommendation, model_version, created_at
            FROM recommendations
            WHERE transaction_id = ?
            """;
        List<RecommendationEntity> list = jdbcTemplate.query(sql, rowMapper, transactionId);
        return list.stream().findFirst();
    }
}
