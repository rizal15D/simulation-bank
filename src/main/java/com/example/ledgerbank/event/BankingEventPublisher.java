package com.example.ledgerbank.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class BankingEventPublisher {
    private final ApplicationEventPublisher events;

    public BankingEventPublisher(ApplicationEventPublisher events) {
        this.events = events;
    }

    public void publish(BankingEvent event) {
        events.publishEvent(event);
    }
}
