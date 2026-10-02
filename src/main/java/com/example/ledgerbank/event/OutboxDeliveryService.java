package com.example.ledgerbank.event;

import com.example.ledgerbank.common.observability.BankingMetrics;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OutboxDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(OutboxDeliveryService.class);

    private final OutboxPersistenceService persistence;
    private final OutboxMessagePublisher publisher;
    private final OutboxProperties properties;
    private final BankingMetrics metrics;
    private final Clock clock;

    @Autowired
    public OutboxDeliveryService(OutboxPersistenceService persistence, OutboxMessagePublisher publisher,
                                 OutboxProperties properties, BankingMetrics metrics) {
        this(persistence, publisher, properties, metrics, Clock.systemUTC());
    }

    OutboxDeliveryService(OutboxPersistenceService persistence, OutboxMessagePublisher publisher,
                          OutboxProperties properties, BankingMetrics metrics, Clock clock) {
        this.persistence = persistence;
        this.publisher = publisher;
        this.properties = properties;
        this.metrics = metrics;
        this.clock = clock;
    }

    public int publishReadyBatch() {
        Instant now = clock.instant();
        List<OutboxEvent> ready = persistence.claimReadyBatch(now);
        ready.forEach(this::publish);
        return ready.size();
    }

    public int removeExpiredPublished() {
        return persistence.removeExpiredPublished(clock.instant().minus(properties.retention()));
    }

    public void refreshBacklogMetrics() {
        OutboxPersistenceService.Backlog backlog = persistence.backlog();
        metrics.refreshOutboxBacklog(backlog.pending(), backlog.dead());
    }

    private void publish(OutboxEvent event) {
        Instant claimDeadline = event.getNextAttemptAt();
        try {
            publisher.publish(event);
        } catch (RuntimeException failure) {
            recordFailure(event, claimDeadline, failure);
            return;
        }

        Instant publishedAt = clock.instant();
        try {
            if (persistence.markPublished(event.getId(), claimDeadline, publishedAt)) {
                metrics.outboxPublished(Duration.between(event.getCreatedAt(), publishedAt));
                log.debug("Published outbox event {} ({})", event.getId(), event.getEventType());
            } else {
                log.warn("Ignored stale success acknowledgement for outbox event {}", event.getId());
            }
        } catch (RuntimeException persistenceFailure) {
            log.error("Outbox event {} was published but its database acknowledgement failed; "
                    + "it will be retried after the claim lease", event.getId(), persistenceFailure);
        }
    }

    private void recordFailure(OutboxEvent event, Instant claimDeadline, RuntimeException failure) {
        int nextAttempt = event.getAttemptCount() + 1;
        boolean exhausted = nextAttempt >= properties.maxAttempts();
        Instant retryAt = clock.instant().plus(backoff(event.getAttemptCount()));
        try {
            if (!persistence.recordFailure(event.getId(), claimDeadline, retryAt, failure.getMessage(), exhausted)) {
                log.warn("Ignored stale failure acknowledgement for outbox event {}", event.getId());
                return;
            }
            metrics.outboxFailure(exhausted);
            if (exhausted) {
                log.error("Outbox event {} ({}) moved to DEAD after {} attempts",
                        event.getId(), event.getEventType(), nextAttempt, failure);
            } else {
                log.warn("Outbox event {} ({}) publish attempt {} failed; next attempt at {}",
                        event.getId(), event.getEventType(), nextAttempt, retryAt);
            }
        } catch (RuntimeException persistenceFailure) {
            log.error("Could not record publish failure for outbox event {}; "
                    + "it will be retried after the claim lease", event.getId(), persistenceFailure);
        }
    }

    private Duration backoff(int previousFailures) {
        int exponent = Math.min(previousFailures, 30);
        Duration calculated;
        try {
            calculated = properties.initialBackoff().multipliedBy(1L << exponent);
        } catch (ArithmeticException overflow) {
            return properties.maxBackoff();
        }
        return calculated.compareTo(properties.maxBackoff()) > 0 ? properties.maxBackoff() : calculated;
    }
}
