package com.example.ledgerbank.event;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OutboxPersistenceService {
    private final OutboxEventRepository outbox;
    private final OutboxProperties properties;

    OutboxPersistenceService(OutboxEventRepository outbox, OutboxProperties properties) {
        this.outbox = outbox;
        this.properties = properties;
    }

    @Transactional
    public List<OutboxEvent> claimReadyBatch(Instant now) {
        Instant claimDeadline = now.plus(properties.claimLease()).truncatedTo(ChronoUnit.MICROS);
        List<OutboxEvent> ready = outbox.lockReadyBatch(now, properties.batchSize());
        ready.forEach(event -> event.claimUntil(claimDeadline));
        return List.copyOf(ready);
    }

    @Transactional
    public boolean markPublished(UUID eventId, Instant claimDeadline, Instant publishedAt) {
        return outbox.lockById(eventId)
                .filter(event -> event.isClaimedUntil(claimDeadline))
                .map(event -> {
                    event.markPublished(publishedAt);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public boolean recordFailure(UUID eventId, Instant claimDeadline, Instant retryAt,
                                 String error, boolean exhausted) {
        return outbox.lockById(eventId)
                .filter(event -> event.isClaimedUntil(claimDeadline))
                .map(event -> {
                    event.recordFailure(retryAt, error, exhausted);
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public int removeExpiredPublished(Instant cutoff) {
        return outbox.deletePublishedBefore(cutoff);
    }

    @Transactional(readOnly = true)
    public Backlog backlog() {
        return new Backlog(outbox.countByStatus(OutboxStatus.PENDING),
                outbox.countByStatus(OutboxStatus.DEAD));
    }

    record Backlog(long pending, long dead) {
    }
}
