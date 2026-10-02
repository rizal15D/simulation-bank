package com.example.ledgerbank.event;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(OutboxDeliveryService.class);

    private final OutboxEventRepository outbox;
    private final OutboxMessagePublisher publisher;
    private final OutboxProperties properties;
    private final Clock clock;

    @Autowired
    public OutboxDeliveryService(OutboxEventRepository outbox, OutboxMessagePublisher publisher,
                                 OutboxProperties properties) {
        this(outbox, publisher, properties, Clock.systemUTC());
    }

    OutboxDeliveryService(OutboxEventRepository outbox, OutboxMessagePublisher publisher,
                          OutboxProperties properties, Clock clock) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public int publishReadyBatch() {
        Instant now = clock.instant();
        List<OutboxEvent> ready = outbox.lockReadyBatch(now, properties.batchSize());
        ready.forEach(event -> publish(event, now));
        return ready.size();
    }

    @Transactional
    public int removeExpiredPublished() {
        return outbox.deletePublishedBefore(clock.instant().minus(properties.retention()));
    }

    private void publish(OutboxEvent event, Instant now) {
        try {
            publisher.publish(event);
            event.markPublished(clock.instant());
            log.debug("Published outbox event {} ({})", event.getId(), event.getEventType());
        } catch (RuntimeException failure) {
            int nextAttempt = event.getAttemptCount() + 1;
            boolean exhausted = nextAttempt >= properties.maxAttempts();
            event.recordFailure(now.plus(backoff(event.getAttemptCount())), failure.getMessage(), exhausted);
            if (exhausted) {
                log.error("Outbox event {} ({}) moved to DEAD after {} attempts",
                        event.getId(), event.getEventType(), nextAttempt, failure);
            } else {
                log.warn("Outbox event {} ({}) publish attempt {} failed; next attempt at {}",
                        event.getId(), event.getEventType(), nextAttempt, event.getNextAttemptAt());
            }
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
