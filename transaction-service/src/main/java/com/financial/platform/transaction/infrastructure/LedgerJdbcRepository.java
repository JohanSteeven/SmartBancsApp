package com.financial.platform.transaction.infrastructure;

import com.financial.platform.transaction.domain.LedgerEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class LedgerJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public LedgerJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<LedgerEntry> ledgerRowMapper = (rs, rowNum) -> new LedgerEntry(
        UUID.fromString(rs.getString("id")),
        UUID.fromString(rs.getString("transaction_id")),
        UUID.fromString(rs.getString("account_id")),
        rs.getString("entry_type"),
        rs.getBigDecimal("amount"),
        rs.getObject("created_at", OffsetDateTime.class)
    );

    public LedgerEntry createEntry(UUID transactionId, UUID accountId, String entryType, BigDecimal amount) {
        UUID id = UUID.randomUUID();
        String sql = """
            INSERT INTO ledger_entries (id, transaction_id, account_id, entry_type, amount, created_at)
            VALUES (?, ?, ?, ?, ?, NOW())
            """;
        jdbcTemplate.update(sql, id, transactionId, accountId, entryType, amount);
        return new LedgerEntry(id, transactionId, accountId, entryType, amount, OffsetDateTime.now());
    }

    public LedgerEntry createDebit(UUID transactionId, UUID accountId, BigDecimal amount) {
        return createEntry(transactionId, accountId, "DEBIT", amount);
    }

    public LedgerEntry createCredit(UUID transactionId, UUID accountId, BigDecimal amount) {
        return createEntry(transactionId, accountId, "CREDIT", amount);
    }

    public List<LedgerEntry> findByTransactionId(UUID transactionId) {
        String sql = """
            SELECT id, transaction_id, account_id, entry_type, amount, created_at
            FROM ledger_entries
            WHERE transaction_id = ?
            ORDER BY created_at ASC
            """;
        return jdbcTemplate.query(sql, ledgerRowMapper, transactionId);
    }
}
