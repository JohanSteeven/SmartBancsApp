package com.financial.platform.transaction.api;

import com.financial.platform.transaction.domain.AccountInactiveException;
import com.financial.platform.transaction.domain.AccountNotFoundException;
import com.financial.platform.transaction.domain.CurrencyMismatchException;
import com.financial.platform.transaction.domain.InsufficientBalanceException;
import com.financial.platform.transaction.domain.SameAccountTransferException;
import com.financial.platform.transaction.domain.TransferBusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.financial.platform.shared.observability.TransactionMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class TransactionExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TransactionExceptionHandler.class);

    private final TransactionMetrics metrics;

    public TransactionExceptionHandler(TransactionMetrics metrics) {
        this.metrics = metrics;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .toList();
        ApiErrorResponse body = ApiErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            "INVALID_INPUT",
            "La solicitud contiene parámetros inválidos",
            details
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        ApiErrorResponse body = ApiErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            "MISSING_HEADER",
            "Falta el encabezado obligatorio: " + ex.getHeaderName()
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({SameAccountTransferException.class, CurrencyMismatchException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception ex) {
        metrics.registerRejectedTransfer("BAD_REQUEST");
        ApiErrorResponse body = ApiErrorResponse.of(
            HttpStatus.BAD_REQUEST.value(),
            "BAD_REQUEST",
            ex.getMessage()
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(AccountNotFoundException ex) {
        metrics.registerRejectedTransfer("ACCOUNT_NOT_FOUND");
        ApiErrorResponse body = ApiErrorResponse.of(
            HttpStatus.NOT_FOUND.value(),
            "ACCOUNT_NOT_FOUND",
            ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler({InsufficientBalanceException.class, AccountInactiveException.class})
    public ResponseEntity<ApiErrorResponse> handleUnprocessable(TransferBusinessException ex) {
        metrics.registerRejectedTransfer(ex.getClass().getSimpleName());
        ApiErrorResponse body = ApiErrorResponse.of(
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            "BUSINESS_RULE_VIOLATION",
            ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler({CannotAcquireLockException.class, DeadlockLoserDataAccessException.class})
    public ResponseEntity<ApiErrorResponse> handleConcurrency(Exception ex) {
        metrics.registerDeadlock();
        log.warn("Conflicto de concurrencia en la base de datos: {}", ex.getMessage());
        ApiErrorResponse body = ApiErrorResponse.of(
            HttpStatus.CONFLICT.value(),
            "CONCURRENCY_CONFLICT",
            "El recurso se encuentra temporalmente bloqueado por otra operación concurente. Por favor reintente."
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        log.error("Error no controlado en el servidor: ", ex);
        ApiErrorResponse body = ApiErrorResponse.of(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "INTERNAL_SERVER_ERROR",
            "Ocurrió un error inesperado al procesar la transferencia"
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
