package com.example.ledgerbank.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class OutboxScheduler {
    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private final OutboxDeliveryService delivery;

    OutboxScheduler(OutboxDeliveryService delivery) {
        this.delivery = delivery;
    }

    @Scheduled(fixedDelayString = "${ledgerbank.outbox.poll-interval}")
    void publishReadyEvents() {
        delivery.publishReadyBatch();
    }

    @Scheduled(fixedDelayString = "${ledgerbank.outbox.cleanup-interval}")
    void cleanPublishedEvents() {
        int deleted = delivery.removeExpiredPublished();
        if (deleted > 0) {
            log.info("Removed {} expired published outbox events", deleted);
        }
    }

    @Scheduled(fixedDelayString = "${ledgerbank.outbox.metrics-refresh-interval}")
    void refreshBacklogMetrics() {
        delivery.refreshBacklogMetrics();
    }
}
