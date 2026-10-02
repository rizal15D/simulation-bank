package com.example.ledgerbank.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEventTest {
    @Test
    void pendingEventTracksDeliveryLifecycleAndBoundsErrors() {
        Instant createdAt = Instant.parse("2026-10-02T00:00:00Z");
        BankingEvent event = BankingEvent.userLogin(UUID.randomUUID(), UUID.randomUUID());
        OutboxEvent outbox = new OutboxEvent(event, "{}", createdAt);

        assertEquals(event.eventId(), outbox.getId());
        assertEquals("USER_LOGIN", outbox.getEventType());
        assertEquals("user.login", outbox.getRoutingKey());
        assertEquals(OutboxStatus.PENDING, outbox.getStatus());
        assertEquals(0, outbox.getAttemptCount());
        assertEquals(createdAt, outbox.getNextAttemptAt());
        assertNull(outbox.getPublishedAt());

        Instant retryAt = createdAt.plusSeconds(10);
        outbox.recordFailure(retryAt, "x".repeat(600), false);
        assertEquals(OutboxStatus.PENDING, outbox.getStatus());
        assertEquals(1, outbox.getAttemptCount());
        assertEquals(retryAt, outbox.getNextAttemptAt());
        assertEquals(500, outbox.getLastError().length());

        outbox.recordFailure(retryAt.plusSeconds(10), "broker unavailable", true);
        assertEquals(OutboxStatus.DEAD, outbox.getStatus());
        assertEquals(2, outbox.getAttemptCount());

        Instant publishedAt = retryAt.plusSeconds(20);
        outbox.markPublished(publishedAt);
        assertEquals(OutboxStatus.PUBLISHED, outbox.getStatus());
        assertEquals(publishedAt, outbox.getPublishedAt());
        assertNull(outbox.getLastError());
    }
}
