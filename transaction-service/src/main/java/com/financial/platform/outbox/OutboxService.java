package com.financial.platform.outbox;

import com.financial.platform.transaction.domain.OutboxEvent;
import com.financial.platform.transaction.infrastructure.OutboxJdbcRepository;
import com.financial.platform.shared.observability.TransactionMetrics;
import io.nats.client.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxService.class);
    private static final String NATS_SUBJECT_TRANSACTION_COMPLETED = "transaction.completed";

    private final OutboxJdbcRepository outboxRepository;
    private final Connection natsConnection;
    private final TransactionMetrics metrics;

    public OutboxService(
        OutboxJdbcRepository outboxRepository,
        @Autowired(required = false) Connection natsConnection,
        TransactionMetrics metrics
    ) {
        this.outboxRepository = outboxRepository;
        this.natsConnection = natsConnection;
        this.metrics = metrics;
    }

    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findPendingEvents(20);
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Procesando {} eventos de outbox pendientes", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                if (natsConnection != null && natsConnection.getStatus() == Connection.Status.CONNECTED) {
                    natsConnection.publish(NATS_SUBJECT_TRANSACTION_COMPLETED, event.payload().getBytes(StandardCharsets.UTF_8));
                    outboxRepository.markAsPublished(event.id());
                    metrics.registerOutboxEventPublished();
                    log.info("Evento Outbox {} publicado exitosamente en NATS subject '{}'", event.id(), NATS_SUBJECT_TRANSACTION_COMPLETED);
                } else {
                    log.warn("Conexión NATS no disponible al intentar publicar evento Outbox {}", event.id());
                    outboxRepository.incrementRetryCount(event.id());
                    metrics.registerOutboxEventFailed();
                }
            } catch (Exception e) {
                log.error("Error al publicar evento Outbox {} en NATS: {}", event.id(), e.getMessage());
                outboxRepository.incrementRetryCount(event.id());
                metrics.registerOutboxEventFailed();
            }
        }
    }
}
