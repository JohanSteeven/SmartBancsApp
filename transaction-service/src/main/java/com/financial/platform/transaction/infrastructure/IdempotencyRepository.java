package com.financial.platform.transaction.infrastructure;

import com.financial.platform.transaction.domain.Transaction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class IdempotencyRepository {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionJdbcRepository transactionRepository;

    public IdempotencyRepository(JdbcTemplate jdbcTemplate, TransactionJdbcRepository transactionRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionRepository = transactionRepository;
    }

    public Optional<Transaction> findTransactionByKey(String idempotencyKey) {
        String sql = "SELECT transaction_id FROM idempotency_keys WHERE key = ?";
        var list = jdbcTemplate.query(sql, (rs, rowNum) -> UUID.fromString(rs.getString("transaction_id")), idempotencyKey);
        if (list.isEmpty()) {
            return Optional.empty();
        }
        return transactionRepository.findById(list.getFirst());
    }

    public void save(String idempotencyKey, UUID transactionId) {
        String sql = "INSERT INTO idempotency_keys (key, transaction_id, created_at) VALUES (?, ?, NOW())";
        jdbcTemplate.update(sql, idempotencyKey, transactionId);
    }
}
