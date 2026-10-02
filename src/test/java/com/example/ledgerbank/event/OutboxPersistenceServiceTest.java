package com.example.ledgerbank.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxPersistenceServiceTest {
    private final OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    private final OutboxProperties properties = new OutboxProperties(10, 3, Duration.ofSeconds(1),
            Duration.ofSeconds(10), Duration.ofSeconds(3), Duration.ofSeconds(30), Duration.ofDays(7));
    private final OutboxPersistenceService persistence = new OutboxPersistenceService(outbox, properties);
    private final Instant now = Instant.parse("2026-10-02T00:00:00.123456789Z");

    @Test
    void claimUsesRecoveryLeaseWithoutCountingDeliveryAttempt() {
        OutboxEvent event = event();
        when(outbox.lockReadyBatch(now, 10)).thenReturn(List.of(event));

        List<OutboxEvent> claimed = persistence.claimReadyBatch(now);

        assertEquals(List.of(event), claimed);
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(0, event.getAttemptCount());
        assertEquals(Instant.parse("2026-10-02T00:00:30.123456Z"), event.getNextAttemptAt());
    }

    @Test
    void staleAcknowledgementsCannotOverwriteNewerClaim() {
        OutboxEvent event = event();
        Instant currentClaim = now.plusSeconds(30);
        event.claimUntil(currentClaim);
        when(outbox.lockById(event.getId())).thenReturn(Optional.of(event));

        assertFalse(persistence.markPublished(event.getId(), now.plusSeconds(10), now));
        assertFalse(persistence.recordFailure(event.getId(), now.plusSeconds(10),
                now.plusSeconds(1), "stale", false));
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(0, event.getAttemptCount());

        assertTrue(persistence.markPublished(event.getId(), currentClaim, now));
        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
    }

    private OutboxEvent event() {
        BankingEvent payload = BankingEvent.userLogin(UUID.randomUUID(), UUID.randomUUID());
        return new OutboxEvent(payload, "payload", now);
    }
}
