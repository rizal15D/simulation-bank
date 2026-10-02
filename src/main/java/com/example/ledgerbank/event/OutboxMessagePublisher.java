package com.example.ledgerbank.event;

interface OutboxMessagePublisher {
    void publish(OutboxEvent event);
}
