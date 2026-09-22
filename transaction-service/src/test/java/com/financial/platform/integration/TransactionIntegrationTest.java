package com.financial.platform.integration;

import com.financial.platform.transaction.api.TransferRequest;
import com.financial.platform.transaction.api.TransferResponse;
import com.financial.platform.transaction.application.AccountService;
import com.financial.platform.transaction.application.TransferService;
import com.financial.platform.transaction.domain.Account;
import com.financial.platform.transaction.domain.LedgerEntry;
import com.financial.platform.transaction.infrastructure.AccountJdbcRepository;
import com.financial.platform.transaction.infrastructure.LedgerJdbcRepository;
import com.financial.platform.transaction.infrastructure.OutboxJdbcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class TransactionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("smartbancs_test")
        .withUsername("test_user")
        .withPassword("test_pass");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountJdbcRepository accountRepository;

    @Autowired
    private LedgerJdbcRepository ledgerRepository;

    @Autowired
    private OutboxJdbcRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID sourceId;
    private UUID destId;

    @BeforeEach
    void setUp() {
        sourceId = UUID.randomUUID();
        destId = UUID.randomUUID();

        // Insertar cuenta origen con saldo 100.00 USD y cuenta destino con saldo 0.00 USD
        jdbcTemplate.update(
            "INSERT INTO accounts (id, account_number, balance, currency, status) VALUES (?, ?, 100.00, 'USD', 'ACTIVE')",
            sourceId, "ACC-SRC-" + sourceId.toString().substring(0, 8)
        );
        jdbcTemplate.update(
            "INSERT INTO accounts (id, account_number, balance, currency, status) VALUES (?, ?, 0.00, 'USD', 'ACTIVE')",
            destId, "ACC-DST-" + destId.toString().substring(0, 8)
        );
    }

    @Test
    @DisplayName("Concurrencia Estricta: 10 transferencias simultáneas de 20 USD contra cuenta de 100 USD (Exactamente 5 exitosas, 0 saldo negativo)")
    void testConcurrentTransfersMaintainBalanceIntegrity() throws InterruptedException {
        int totalThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<TransferResponse> responses = new ArrayList<>();

        for (int i = 0; i < totalThreads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    latch.await(); // Sincroniza inicio simultáneo de todos los hilos
                    String idempotencyKey = "CONCURRENCY-KEY-" + index + "-" + UUID.randomUUID();
                    TransferRequest request = new TransferRequest(sourceId, destId, new BigDecimal("20.00"), "USD", "Transferencia concurrente " + index);
                    
                    TransferResponse resp = transferService.executeTransfer(idempotencyKey, request);
                    synchronized (responses) {
                        responses.add(resp);
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown(); // Liberar todos los hilos al tiempo
        doneLatch.await(); // Esperar a que terminen
        executor.shutdown();

        // 1. Verificación de conteo de transferencias
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failureCount.get()).isEqualTo(5);

        // 2. Verificación de saldos finales en PostgreSQL
        Account sourceFinal = accountService.getAccountById(sourceId);
        Account destFinal = accountService.getAccountById(destId);

        assertThat(sourceFinal.balance()).isEqualByComparingTo("0.00");
        assertThat(destFinal.balance()).isEqualByComparingTo("100.00");

        // 3. Verificación de paridad contable en Ledger
        for (TransferResponse resp : responses) {
            List<LedgerEntry> entries = ledgerRepository.findByTransactionId(resp.transactionId());
            assertThat(entries).hasSize(2);
            assertThat(entries).anyMatch(e -> e.entryType().equals("DEBIT") && e.amount().compareTo(new BigDecimal("20.00")) == 0);
            assertThat(entries).anyMatch(e -> e.entryType().equals("CREDIT") && e.amount().compareTo(new BigDecimal("20.00")) == 0);
        }
    }
}
