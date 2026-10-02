package com.example.ledgerbank.event;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class BankingEventPublisher {
    private final OutboxEventRepository outbox;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public BankingEventPublisher(OutboxEventRepository outbox, ObjectMapper json) {
        this(outbox, json, Clock.systemUTC());
    }

    BankingEventPublisher(OutboxEventRepository outbox, ObjectMapper json, Clock clock) {
        this.outbox = outbox;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public void publish(BankingEvent event) {
        try {
            outbox.save(new OutboxEvent(event, json.writeValueAsString(event), clock.instant()));
        } catch (JacksonException invalidEvent) {
            throw new IllegalStateException("Banking event could not be serialized for the outbox", invalidEvent);
        }
    }
}
