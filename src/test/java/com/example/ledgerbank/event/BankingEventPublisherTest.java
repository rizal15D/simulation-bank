package com.example.ledgerbank.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.ObjectMapper;

class BankingEventPublisherTest {
    private final ApplicationEventPublisher applicationEvents = Mockito.mock(ApplicationEventPublisher.class);
    private final OutboxEventRepository outbox = Mockito.mock(OutboxEventRepository.class);
    private final ObjectMapper json = Mockito.mock(ObjectMapper.class);
    private final Instant now = Instant.parse("2026-10-02T00:00:00Z");
    private final BankingEventPublisher publisher = new BankingEventPublisher(
            applicationEvents, outbox, json, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void eventIsPersistedBeforeAfterCommitCompatibilitySignal() throws Exception {
        BankingEvent event = BankingEvent.userLogin(UUID.randomUUID(), UUID.randomUUID());
        when(json.writeValueAsString(event)).thenReturn("serialized-event");
        when(outbox.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        publisher.publish(event);

        ArgumentCaptor<OutboxEvent> saved = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(saved.capture());
        assertEquals(event.eventId(), saved.getValue().getId());
        assertEquals("USER_LOGIN", saved.getValue().getEventType());
        assertEquals("user.login", saved.getValue().getRoutingKey());
        assertEquals("serialized-event", saved.getValue().getPayload());
        assertEquals(now, saved.getValue().getCreatedAt());
        verify(applicationEvents).publishEvent(event);
    }
}
