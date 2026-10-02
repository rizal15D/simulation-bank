package com.example.ledgerbank.common.observability;

import com.example.ledgerbank.common.exception.BusinessException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class BankingMetrics {
    private final MeterRegistry registry;
    private final Counter outboxPublished;
    private final Counter outboxRetries;
    private final Counter outboxDead;
    private final Timer outboxDelivery;
    private final AtomicLong pendingBacklog = new AtomicLong();
    private final AtomicLong deadBacklog = new AtomicLong();

    public BankingMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.outboxPublished = registry.counter("ledgerbank.outbox.published");
        this.outboxRetries = registry.counter("ledgerbank.outbox.retries");
        this.outboxDead = registry.counter("ledgerbank.outbox.dead");
        this.outboxDelivery = registry.timer("ledgerbank.outbox.delivery");
        Gauge.builder("ledgerbank.outbox.backlog", pendingBacklog, AtomicLong::get)
                .tag("status", "pending").register(registry);
        Gauge.builder("ledgerbank.outbox.backlog", deadBacklog, AtomicLong::get)
                .tag("status", "dead").register(registry);
    }

    public Timer.Sample startTransfer() {
        return Timer.start(registry);
    }

    public void completeTransfer(Timer.Sample sample, RuntimeException failure) {
        String outcome = failure == null ? "success" : "failure";
        String reason = failure == null ? "none" : failureReason(failure);
        registry.counter("ledgerbank.transfer.requests", "outcome", outcome, "reason", reason).increment();
        sample.stop(Timer.builder("ledgerbank.transfer.duration")
                .tags("outcome", outcome, "reason", reason)
                .register(registry));
    }

    public void outboxPublished(Duration deliveryTime) {
        outboxPublished.increment();
        outboxDelivery.record(deliveryTime.isNegative() ? Duration.ZERO : deliveryTime);
    }

    public void outboxFailure(boolean exhausted) {
        if (exhausted) {
            outboxDead.increment();
        } else {
            outboxRetries.increment();
        }
    }

    public void refreshOutboxBacklog(long pending, long dead) {
        pendingBacklog.set(pending);
        deadBacklog.set(dead);
    }

    private String failureReason(RuntimeException failure) {
        return failure instanceof BusinessException business ? business.getCode() : "UNEXPECTED";
    }
}
