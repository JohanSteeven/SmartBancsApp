package com.financial.platform.sync;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Component
public class BancsSyncWorker {

    private static final Logger log = LoggerFactory.getLogger(BancsSyncWorker.class);
    private static final String NATS_SUBJECT_TRANSACTION_COMPLETED = "transaction.completed";

    private final Connection natsConnection;
    private final JdbcTemplate jdbcTemplate;

    public BancsSyncWorker(@Autowired(required = false) Connection natsConnection, JdbcTemplate jdbcTemplate) {
        this.natsConnection = natsConnection;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void startListener() {
        if (natsConnection == null) {
            log.warn("NATS connection is null. BancsSyncWorker will not start listening.");
            return;
        }

        try {
            Dispatcher dispatcher = natsConnection.createDispatcher(msg -> {
                String payload = new String(msg.getData(), StandardCharsets.UTF_8);
                log.info("BancsSyncWorker recibió evento de NATS: {}", payload);
                processEvent(payload);
            });
            dispatcher.subscribe(NATS_SUBJECT_TRANSACTION_COMPLETED);
            log.info("BancsSyncWorker suscrito exitosamente al tema NATS '{}'", NATS_SUBJECT_TRANSACTION_COMPLETED);
        } catch (Exception e) {
            log.error("Error al suscribir BancsSyncWorker a NATS: {}", e.getMessage(), e);
        }
    }

    public void processEvent(String payloadJson) {
        try {
            // Parser básico JSON evitando dependencias pesadas
            String txIdStr = extractJsonField(payloadJson, "transactionId");
            String sourceStr = extractJsonField(payloadJson, "sourceAccountId");
            String destStr = extractJsonField(payloadJson, "destinationAccountId");
            String amountStr = extractJsonField(payloadJson, "amount");
            String currency = extractJsonField(payloadJson, "currency");
            String status = extractJsonField(payloadJson, "status");

            UUID id = UUID.randomUUID();
            UUID txId = UUID.fromString(txIdStr);
            UUID sourceId = UUID.fromString(sourceStr);
            UUID destId = UUID.fromString(destStr);
            BigDecimal amount = new BigDecimal(amountStr);

            String sql = """
                INSERT INTO bancs_sync_staging (id, transaction_id, source_account_id, destination_account_id, amount, currency, status, processed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
                ON CONFLICT (id) DO NOTHING
                """;
            jdbcTemplate.update(sql, id, txId, sourceId, destId, amount, currency, status);
            log.info("Sincronización Bancs Staging completada exitosamente para transacción {}", txId);
        } catch (Exception e) {
            log.error("Error al procesar y guardar evento de sincronización Bancs: {}", e.getMessage(), e);
        }
    }

    private String extractJsonField(String json, String field) {
        String key = "\"" + field + "\":";
        int idx = json.indexOf(key);
        if (idx == -1) return "";
        int start = idx + key.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"')) {
            start++;
        }
        int end = start;
        while (end < json.length() && json.charAt(end) != '"' && json.charAt(end) != ',' && json.charAt(end) != '}') {
            end++;
        }
        return json.substring(start, end).trim();
    }
}
