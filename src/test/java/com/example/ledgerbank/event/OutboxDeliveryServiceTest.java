package com.example.ledgerbank.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ledgerbank.common.observability.BankingMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class OutboxDeliveryServiceTest {
    private final OutboxEventRepository outbox = Mockito.mock(OutboxEventRepository.class);
    private final OutboxMessagePublisher publisher = Mockito.mock(OutboxMessagePublisher.class);
    private final Instant now = Instant.parse("2026-10-02T00:00:00Z");
    private final OutboxProperties properties = new OutboxProperties(10, 2, Duration.ofSeconds(2),
            Duration.ofSeconds(5), Duration.ofSeconds(3), Duration.ofDays(7));
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final OutboxDeliveryService delivery = new OutboxDeliveryService(
            outbox, publisher, properties, new BankingMetrics(registry), Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void confirmedEventsAreMarkedPublished() {
        OutboxEvent event = event();
        when(outbox.lockReadyBatch(now, 10)).thenReturn(List.of(event));

        assertEquals(1, delivery.publishReadyBatch());

        verify(publisher).publish(event);
        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertEquals(now, event.getPublishedAt());
        assertNull(event.getLastError());
    }

    @Test
    void failuresUseBoundedExponentialBackoffAndEventuallyBecomeDead() {
        OutboxEvent event = event();
        when(outbox.lockReadyBatch(now, 10)).thenReturn(List.of(event));
        doThrow(new IllegalStateException("broker unavailable")).when(publisher).publish(event);

        delivery.publishReadyBatch();

        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(1, event.getAttemptCount());
        assertEquals(now.plusSeconds(2), event.getNextAttemptAt());

        delivery.publishReadyBatch();

        assertEquals(OutboxStatus.DEAD, event.getStatus());
        assertEquals(2, event.getAttemptCount());
        assertEquals(now.plusSeconds(4), event.getNextAttemptAt());
        assertEquals("broker unavailable", event.getLastError());
    }

    @Test
    void publishedRetentionUsesConfiguredCutoff() {
        when(outbox.deletePublishedBefore(now.minus(Duration.ofDays(7)))).thenReturn(3);

        assertEquals(3, delivery.removeExpiredPublished());

        verify(outbox).deletePublishedBefore(now.minus(Duration.ofDays(7)));
    }

    private OutboxEvent event() {
        BankingEvent payload = BankingEvent.userLogin(UUID.randomUUID(), UUID.randomUUID());
        return new OutboxEvent(payload, "payload", now);
    }
}
