package com.example.ledgerbank.common.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.ledgerbank.common.exception.BusinessException;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class BankingMetricsTest {
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final BankingMetrics metrics = new BankingMetrics(registry);

    @Test
    void transferOutcomeUsesBoundedBusinessTags() {
        Timer.Sample success = metrics.startTransfer();
        metrics.completeTransfer(success, null);
        Timer.Sample failure = metrics.startTransfer();
        metrics.completeTransfer(failure,
                new BusinessException("INSUFFICIENT_BALANCE", "not enough", HttpStatus.CONFLICT));

        assertEquals(1.0, registry.counter("ledgerbank.transfer.requests",
                "outcome", "success", "reason", "none").count());
        assertEquals(1.0, registry.counter("ledgerbank.transfer.requests",
                "outcome", "failure", "reason", "INSUFFICIENT_BALANCE").count());
        assertEquals(1L, registry.timer("ledgerbank.transfer.duration",
                "outcome", "success", "reason", "none").count());
    }

    @Test
    void outboxMetricsExposeDeliveryRetryDeadAndBacklog() {
        metrics.outboxPublished(Duration.ofSeconds(3));
        metrics.outboxFailure(false);
        metrics.outboxFailure(true);
        metrics.refreshOutboxBacklog(7, 2);

        assertEquals(1.0, registry.counter("ledgerbank.outbox.published").count());
        assertEquals(1.0, registry.counter("ledgerbank.outbox.retries").count());
        assertEquals(1.0, registry.counter("ledgerbank.outbox.dead").count());
        assertEquals(1L, registry.timer("ledgerbank.outbox.delivery").count());
        assertEquals(7.0, registry.get("ledgerbank.outbox.backlog").tag("status", "pending").gauge().value());
        assertEquals(2.0, registry.get("ledgerbank.outbox.backlog").tag("status", "dead").gauge().value());
    }
}
