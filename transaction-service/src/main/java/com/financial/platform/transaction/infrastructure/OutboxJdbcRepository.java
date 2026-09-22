package com.financial.platform.transaction.infrastructure;

import com.financial.platform.transaction.domain.OutboxEvent;
import com.financial.platform.transaction.domain.Transaction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class OutboxJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public OutboxJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<OutboxEvent> outboxRowMapper = (rs, rowNum) -> new OutboxEvent(
        UUID.fromString(rs.getString("id")),
        rs.getString("aggregate_type"),
        UUID.fromString(rs.getString("aggregate_id")),
        rs.getString("event_type"),
        rs.getString("payload"),
        rs.getString("status"),
        rs.getInt("retry_count"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("published_at", OffsetDateTime.class)
    );

    public OutboxEvent storeTransactionCompletedEvent(Transaction transaction) {
        UUID id = UUID.randomUUID();
        String jsonPayload = String.format(
            "{\"transactionId\":\"%s\",\"sourceAccountId\":\"%s\",\"destinationAccountId\":\"%s\",\"amount\":%s,\"currency\":\"%s\",\"status\":\"%s\"}",
            transaction.id(),
            transaction.sourceAccountId(),
            transaction.destinationAccountId(),
            transaction.amount().toPlainString(),
            transaction.currency(),
            transaction.status()
        );

        String sql = """
            INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, status, retry_count, created_at)
            VALUES (?, 'TRANSACTION', ?, 'TRANSACTION_COMPLETED', ?::jsonb, 'PENDING', 0, NOW())
            """;
        jdbcTemplate.update(sql, id, transaction.id(), jsonPayload);
        return new OutboxEvent(id, "TRANSACTION", transaction.id(), "TRANSACTION_COMPLETED", jsonPayload, "PENDING", 0, OffsetDateTime.now(), null);
    }

    public List<OutboxEvent> findPendingEvents(int limit) {
        String sql = """
            SELECT id, aggregate_type, aggregate_id, event_type, payload, status, retry_count, created_at, published_at
            FROM outbox_events
            WHERE status = 'PENDING' AND retry_count < 5
            ORDER BY created_at ASC
            LIMIT ?
            """;
        return jdbcTemplate.query(sql, outboxRowMapper, limit);
    }

    public void markAsPublished(UUID id) {
        String sql = "UPDATE outbox_events SET status = 'PUBLISHED', published_at = NOW() WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }

    public void incrementRetryCount(UUID id) {
        String sql = "UPDATE outbox_events SET retry_count = retry_count + 1 WHERE id = ?";
        jdbcTemplate.update(sql, id);
    }
}
