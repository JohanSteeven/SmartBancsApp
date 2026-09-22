package com.financial.platform.transaction.api;

import com.financial.platform.transaction.application.TransferService;
import com.financial.platform.transaction.domain.Transaction;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions", description = "Endpoints para ejecución y consulta de transferencias financieras")
public class TransactionController {

    private final TransferService transferService;

    public TransactionController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ejecutar transferencia atómica e idempotente entre cuentas")
    public TransferResponse transfer(
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody TransferRequest request
    ) {
        return transferService.executeTransfer(idempotencyKey, request);
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Consultar estado e información de una transferencia por ID")
    public TransferResponse getTransaction(@PathVariable UUID transactionId) {
        Transaction transaction = transferService.getTransactionById(transactionId)
            .orElseThrow(() -> new IllegalArgumentException("No se encontró la transacción con ID: " + transactionId));
        return TransferResponse.from(transaction, false);
    }
}
