package com.financial.platform.transaction.infrastructure;

import com.financial.platform.transaction.domain.Transaction;
import com.financial.platform.transaction.domain.TransactionStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TransactionJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public TransactionJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Transaction> transactionRowMapper = (rs, rowNum) -> new Transaction(
        UUID.fromString(rs.getString("id")),
        UUID.fromString(rs.getString("source_account_id")),
        UUID.fromString(rs.getString("destination_account_id")),
        rs.getBigDecimal("amount"),
        rs.getString("currency").trim(),
        TransactionStatus.valueOf(rs.getString("status")),
        rs.getString("idempotency_key"),
        rs.getObject("created_at", OffsetDateTime.class)
    );

    public Transaction create(
        UUID id,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency,
        TransactionStatus status,
        String idempotencyKey
    ) {
        String sql = """
            INSERT INTO transactions (id, source_account_id, destination_account_id, amount, currency, status, idempotency_key, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
            """;
        jdbcTemplate.update(sql, id, sourceAccountId, destinationAccountId, amount, currency, status.name(), idempotencyKey);
        return findById(id).orElseThrow();
    }

    public Optional<Transaction> findById(UUID id) {
        String sql = """
            SELECT id, source_account_id, destination_account_id, amount, currency, status, idempotency_key, created_at
            FROM transactions
            WHERE id = ?
            """;
        List<Transaction> list = jdbcTemplate.query(sql, transactionRowMapper, id);
        return list.stream().findFirst();
    }
}
