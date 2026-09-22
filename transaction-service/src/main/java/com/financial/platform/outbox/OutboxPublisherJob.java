package com.financial.platform.outbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisherJob {

    private final OutboxService outboxService;

    public OutboxPublisherJob(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.delay-ms:3000}")
    public void publishPendingEvents() {
        outboxService.publishPendingEvents();
    }
}
