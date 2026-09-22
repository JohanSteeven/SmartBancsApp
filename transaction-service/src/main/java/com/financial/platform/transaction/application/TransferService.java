package com.financial.platform.transaction.application;

import com.financial.platform.transaction.api.TransferRequest;
import com.financial.platform.transaction.api.TransferResponse;
import com.financial.platform.transaction.domain.Account;
import com.financial.platform.transaction.domain.AccountInactiveException;
import com.financial.platform.transaction.domain.CurrencyMismatchException;
import com.financial.platform.transaction.domain.InsufficientBalanceException;
import com.financial.platform.transaction.domain.SameAccountTransferException;
import com.financial.platform.transaction.domain.Transaction;
import com.financial.platform.transaction.domain.TransactionStatus;
import com.financial.platform.transaction.infrastructure.AccountJdbcRepository;
import com.financial.platform.transaction.infrastructure.IdempotencyRepository;
import com.financial.platform.transaction.infrastructure.LedgerJdbcRepository;
import com.financial.platform.transaction.infrastructure.OutboxJdbcRepository;
import com.financial.platform.transaction.infrastructure.TransactionJdbcRepository;
import com.financial.platform.shared.observability.TransactionMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final TransactionTemplate transactionTemplate;
    private final AccountJdbcRepository accountRepository;
    private final TransactionJdbcRepository transactionRepository;
    private final LedgerJdbcRepository ledgerRepository;
    private final OutboxJdbcRepository outboxRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final TransactionMetrics metrics;

    public TransferService(
        TransactionTemplate transactionTemplate,
        AccountJdbcRepository accountRepository,
        TransactionJdbcRepository transactionRepository,
        LedgerJdbcRepository ledgerRepository,
        OutboxJdbcRepository outboxRepository,
        IdempotencyRepository idempotencyRepository,
        TransactionMetrics metrics
    ) {
        this.transactionTemplate = transactionTemplate;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerRepository = ledgerRepository;
        this.outboxRepository = outboxRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.metrics = metrics;
    }

    public TransferResponse executeTransfer(String idempotencyKey, TransferRequest request) {
        long startTime = System.currentTimeMillis();

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("El header Idempotency-Key es obligatorio");
        }

        // 1. Verificación previa de idempotencia (Fast path)
        Optional<Transaction> existingTransaction = idempotencyRepository.findTransactionByKey(idempotencyKey);
        if (existingTransaction.isPresent()) {
            log.info("Solicitud idempotente detectada para clave {}. Retornando transacción existente {}", idempotencyKey, existingTransaction.get().id());
            metrics.registerIdempotentResponse();
            return TransferResponse.from(existingTransaction.get(), true);
        }

        if (request.sourceAccountId().equals(request.destinationAccountId())) {
            throw new SameAccountTransferException("La cuenta de origen y destino no pueden ser la misma");
        }

        // 2. Ejecución con reintentos controlados para bloqueos transitorios
        int maxRetries = 3;
        int attempts = 0;
        while (true) {
            attempts++;
            try {
                TransferResponse response = performAtomicTransfer(idempotencyKey, request);
                metrics.recordTransferDuration(System.currentTimeMillis() - startTime);
                return response;
            } catch (CannotAcquireLockException | DeadlockLoserDataAccessException e) {
                if (attempts >= maxRetries) {
                    log.error("Excedido el número máximo de reintentos por bloqueo concurrente para transferencia de {}", request.sourceAccountId(), e);
                    throw e;
                }
                log.warn("Bloqueo transitorio detectado en intento {}/{}. Reintentando...", attempts, maxRetries);
                try {
                    Thread.sleep(50L * attempts);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupción durante reintento de transferencia", ie);
                }
            }
        }
    }

    private TransferResponse performAtomicTransfer(String idempotencyKey, TransferRequest request) {
        return transactionTemplate.execute(status -> {
            // Re-verificar idempotencia dentro de la transacción por si otra petición concurrente avanzó
            Optional<Transaction> doubleCheck = idempotencyRepository.findTransactionByKey(idempotencyKey);
            if (doubleCheck.isPresent()) {
                return TransferResponse.from(doubleCheck.get(), true);
            }

            // 3. Bloqueo estable por UUID en PostgreSQL (SELECT ... FOR UPDATE ORDER BY id ASC)
            var lockedAccounts = accountRepository.lockAccountsInStableOrder(
                request.sourceAccountId(),
                request.destinationAccountId()
            );

            Account sourceAccount = lockedAccounts.source();
            Account destinationAccount = lockedAccounts.destination();

            // 4. Validaciones de negocio
            validateAccountsState(sourceAccount, destinationAccount, request);

            // 5. Débito y Crédito
            accountRepository.debit(sourceAccount.id(), request.amount());
            accountRepository.credit(destinationAccount.id(), request.amount());

            // 6. Registro de Transacción
            UUID transactionId = UUID.randomUUID();
            Transaction transaction = transactionRepository.create(
                transactionId,
                sourceAccount.id(),
                destinationAccount.id(),
                request.amount(),
                request.currency(),
                TransactionStatus.COMPLETED,
                idempotencyKey
            );

            // 7. Registro de Partida Doble en Ledger (DEBIT y CREDIT)
            ledgerRepository.createDebit(transactionId, sourceAccount.id(), request.amount());
            ledgerRepository.createCredit(transactionId, destinationAccount.id(), request.amount());

            // 8. Evento Outbox para procesamiento asíncrono NATS
            outboxRepository.storeTransactionCompletedEvent(transaction);

            // 9. Guardar clave de Idempotencia
            idempotencyRepository.save(idempotencyKey, transactionId);

            log.info("Transferencia exitosa {} de {} a {} por valor de {} {}",
                transactionId, sourceAccount.id(), destinationAccount.id(), request.amount(), request.currency());

            metrics.registerSuccessfulTransfer();

            return TransferResponse.from(transaction, false);
        });
    }

    private void validateAccountsState(Account source, Account destination, TransferRequest request) {
        if (!source.isActive()) {
            throw new AccountInactiveException("La cuenta de origen " + source.id() + " no está activa");
        }
        if (!destination.isActive()) {
            throw new AccountInactiveException("La cuenta de destino " + destination.id() + " no está activa");
        }
        if (!source.currency().equalsIgnoreCase(request.currency())) {
            throw new CurrencyMismatchException("La moneda de la solicitud (" + request.currency() + ") no coincide con la moneda de la cuenta origen (" + source.currency() + ")");
        }
        if (!destination.currency().equalsIgnoreCase(request.currency())) {
            throw new CurrencyMismatchException("La moneda de la solicitud (" + request.currency() + ") no coincide con la moneda de la cuenta destino (" + destination.currency() + ")");
        }
        if (!source.hasSufficientBalance(request.amount())) {
            throw new InsufficientBalanceException("Saldo insuficiente en la cuenta origen " + source.id() + ". Saldo disponible: " + source.balance() + " " + source.currency());
        }
    }

    public Optional<Transaction> getTransactionById(UUID transactionId) {
        return transactionRepository.findById(transactionId);
    }
}
