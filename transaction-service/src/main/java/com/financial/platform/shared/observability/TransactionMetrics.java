package com.financial.platform.shared.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class TransactionMetrics {

    private final MeterRegistry meterRegistry;
    private final Timer transferExecutionTimer;

    public TransactionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.transferExecutionTimer = Timer.builder("financial_transfer_execution_seconds")
            .description("Distribución del tiempo de ejecución de transferencias financieras")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
    }

    public void recordTransferDuration(long durationMs) {
        transferExecutionTimer.record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void registerSuccessfulTransfer() {
        Counter.builder("financial_transactions_total")
            .description("Total de transferencias procesadas")
            .tag("status", "completed")
            .register(meterRegistry)
            .increment();
    }

    public void registerIdempotentResponse() {
        Counter.builder("financial_transactions_total")
            .description("Total de transferencias procesadas")
            .tag("status", "idempotent")
            .register(meterRegistry)
            .increment();
    }

    public void registerRejectedTransfer(String reason) {
        Counter.builder("financial_transactions_total")
            .description("Total de transferencias procesadas")
            .tag("status", "rejected")
            .tag("reason", reason)
            .register(meterRegistry)
            .increment();
    }

    public void registerDeadlock() {
        Counter.builder("financial_database_deadlocks_total")
            .description("Total de colisiones de bloqueo pesimista en base de datos")
            .register(meterRegistry)
            .increment();
    }

    public void registerOutboxEventPublished() {
        Counter.builder("financial_outbox_events_total")
            .description("Total de eventos de outbox procesados")
            .tag("status", "published")
            .register(meterRegistry)
            .increment();
    }

    public void registerOutboxEventFailed() {
        Counter.builder("financial_outbox_events_total")
            .description("Total de eventos de outbox procesados")
            .tag("status", "failed")
            .register(meterRegistry)
            .increment();
    }

    public void registerAiEvaluation(String recommendation, String modelVersion) {
        Counter.builder("financial_ai_evaluations_total")
            .description("Total de evaluaciones de riesgo de IA")
            .tag("recommendation", recommendation)
            .tag("model", modelVersion)
            .register(meterRegistry)
            .increment();
    }
}
