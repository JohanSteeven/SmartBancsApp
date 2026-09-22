package com.financial.platform.transaction.application;

import com.financial.platform.transaction.api.TransferRequest;
import com.financial.platform.transaction.api.TransferResponse;
import com.financial.platform.transaction.domain.Account;
import com.financial.platform.transaction.domain.SameAccountTransferException;
import com.financial.platform.transaction.domain.Transaction;
import com.financial.platform.transaction.domain.TransactionStatus;
import com.financial.platform.transaction.infrastructure.AccountJdbcRepository;
import com.financial.platform.transaction.infrastructure.IdempotencyRepository;
import com.financial.platform.transaction.infrastructure.LedgerJdbcRepository;
import com.financial.platform.transaction.infrastructure.OutboxJdbcRepository;
import com.financial.platform.transaction.infrastructure.TransactionJdbcRepository;
import com.financial.platform.shared.observability.TransactionMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private AccountJdbcRepository accountRepository;
    @Mock
    private TransactionJdbcRepository transactionRepository;
    @Mock
    private LedgerJdbcRepository ledgerRepository;
    @Mock
    private OutboxJdbcRepository outboxRepository;
    @Mock
    private IdempotencyRepository idempotencyRepository;
    @Mock
    private TransactionMetrics metrics;

    private TransferService transferService;

    private final UUID sourceId = UUID.randomUUID();
    private final UUID destId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        transferService = new TransferService(
            transactionTemplate,
            accountRepository,
            transactionRepository,
            ledgerRepository,
            outboxRepository,
            idempotencyRepository,
            metrics
        );
    }

    @Test
    @DisplayName("Debe retornar la transacción existente si la clave de idempotencia ya fue procesada")
    void testIdempotencyReturnsExistingTransaction() {
        String key = "IDEM-KEY-001";
        Transaction existing = new Transaction(
            UUID.randomUUID(), sourceId, destId, new BigDecimal("100.00"), "USD",
            TransactionStatus.COMPLETED, key, OffsetDateTime.now()
        );

        when(idempotencyRepository.findTransactionByKey(key)).thenReturn(Optional.of(existing));

        TransferRequest request = new TransferRequest(sourceId, destId, new BigDecimal("100.00"), "USD", "Test transfer");
        TransferResponse response = transferService.executeTransfer(key, request);

        assertThat(response).isNotNull();
        assertThat(response.isIdempotentResponse()).isTrue();
        assertThat(response.transactionId()).isEqualTo(existing.id());
        verifyNoInteractions(accountRepository, transactionTemplate);
        verify(metrics).registerIdempotentResponse();
    }

    @Test
    @DisplayName("Debe lanzar SameAccountTransferException si la cuenta origen y destino son iguales")
    void testSameAccountTransferThrowsException() {
        String key = "IDEM-KEY-002";
        TransferRequest request = new TransferRequest(sourceId, sourceId, new BigDecimal("50.00"), "USD", "Same account");

        when(idempotencyRepository.findTransactionByKey(key)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transferService.executeTransfer(key, request))
            .isInstanceOf(SameAccountTransferException.class)
            .hasMessageContaining("origen y destino no pueden ser la misma");
    }

    @Test
    @DisplayName("Debe ejecutar la transferencia de forma atómica bajo flujo normal")
    void testSuccessfulTransfer() {
        String key = "IDEM-KEY-003";
        TransferRequest request = new TransferRequest(sourceId, destId, new BigDecimal("200.00"), "USD", "Pago de prueba");

        Account source = new Account(sourceId, "ACC-1", new BigDecimal("1000.00"), "USD", "ACTIVE", OffsetDateTime.now(), OffsetDateTime.now());
        Account dest = new Account(destId, "ACC-2", new BigDecimal("500.00"), "USD", "ACTIVE", OffsetDateTime.now(), OffsetDateTime.now());
        Transaction createdTx = new Transaction(UUID.randomUUID(), sourceId, destId, new BigDecimal("200.00"), "USD", TransactionStatus.COMPLETED, key, OffsetDateTime.now());

        when(idempotencyRepository.findTransactionByKey(key)).thenReturn(Optional.empty());

        // Simula la ejecución sincrónica de transactionTemplate.execute()
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<TransferResponse> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(org.springframework.transaction.TransactionStatus.class));
        });

        when(accountRepository.lockAccountsInStableOrder(sourceId, destId))
            .thenReturn(new AccountJdbcRepository.LockedAccounts(source, dest));
        when(transactionRepository.create(any(), eq(sourceId), eq(destId), eq(new BigDecimal("200.00")), eq("USD"), eq(TransactionStatus.COMPLETED), eq(key)))
            .thenReturn(createdTx);

        TransferResponse response = transferService.executeTransfer(key, request);

        assertThat(response).isNotNull();
        assertThat(response.isIdempotentResponse()).isFalse();
        assertThat(response.amount()).isEqualByComparingTo("200.00");

        verify(accountRepository).debit(sourceId, new BigDecimal("200.00"));
        verify(accountRepository).credit(destId, new BigDecimal("200.00"));
        verify(ledgerRepository).createDebit(any(), eq(sourceId), eq(new BigDecimal("200.00")));
        verify(ledgerRepository).createCredit(any(), eq(destId), eq(new BigDecimal("200.00")));
        verify(outboxRepository).storeTransactionCompletedEvent(createdTx);
        verify(idempotencyRepository).save(key, createdTx.id());
        verify(metrics).registerSuccessfulTransfer();
    }
}
