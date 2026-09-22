package com.financial.platform.transaction.infrastructure;

import com.financial.platform.transaction.domain.Account;
import com.financial.platform.transaction.domain.AccountNotFoundException;
import com.financial.platform.transaction.domain.InsufficientBalanceException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AccountJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public AccountJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record LockedAccounts(Account source, Account destination) {}

    private final RowMapper<Account> accountRowMapper = (rs, rowNum) -> new Account(
        UUID.fromString(rs.getString("id")),
        rs.getString("account_number"),
        rs.getBigDecimal("balance"),
        rs.getString("currency").trim(),
        rs.getString("status"),
        rs.getObject("created_at", OffsetDateTime.class),
        rs.getObject("updated_at", OffsetDateTime.class)
    );

    /**
     * Locks both accounts in deterministic ascending UUID order to prevent deadlocks under high concurrency.
     */
    public LockedAccounts lockAccountsInStableOrder(UUID sourceId, UUID destId) {
        String sql = """
            SELECT id, account_number, balance, currency, status, created_at, updated_at
            FROM accounts
            WHERE id IN (?, ?)
            ORDER BY id ASC
            FOR UPDATE
            """;

        List<Account> accounts = jdbcTemplate.query(sql, accountRowMapper, sourceId, destId);

        Account source = accounts.stream()
            .filter(a -> a.id().equals(sourceId))
            .findFirst()
            .orElseThrow(() -> new AccountNotFoundException(sourceId));

        Account dest = accounts.stream()
            .filter(a -> a.id().equals(destId))
            .findFirst()
            .orElseThrow(() -> new AccountNotFoundException(destId));

        return new LockedAccounts(source, dest);
    }

    public void debit(UUID accountId, BigDecimal amount) {
        String sql = """
            UPDATE accounts
            SET balance = balance - ?, updated_at = NOW()
            WHERE id = ? AND balance >= ?
            """;
        int updated = jdbcTemplate.update(sql, amount, accountId, amount);
        if (updated == 0) {
            throw new InsufficientBalanceException("Saldo insuficiente para debitar " + amount + " de la cuenta " + accountId);
        }
    }

    public void credit(UUID accountId, BigDecimal amount) {
        String sql = """
            UPDATE accounts
            SET balance = balance + ?, updated_at = NOW()
            WHERE id = ?
            """;
        jdbcTemplate.update(sql, amount, accountId);
    }

    public Optional<Account> findById(UUID accountId) {
        String sql = """
            SELECT id, account_number, balance, currency, status, created_at, updated_at
            FROM accounts
            WHERE id = ?
            """;
        List<Account> list = jdbcTemplate.query(sql, accountRowMapper, accountId);
        return list.stream().findFirst();
    }
}
