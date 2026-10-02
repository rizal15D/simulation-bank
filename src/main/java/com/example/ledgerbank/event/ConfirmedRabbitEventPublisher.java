package com.example.ledgerbank.event;

import com.example.ledgerbank.common.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
class ConfirmedRabbitEventPublisher implements OutboxMessagePublisher {
    private final RabbitTemplate rabbit;
    private final ObjectMapper json;
    private final OutboxProperties properties;

    ConfirmedRabbitEventPublisher(RabbitTemplate rabbit, ObjectMapper json, OutboxProperties properties) {
        this.rabbit = rabbit;
        this.json = json;
        this.properties = properties;
    }

    @Override
    public void publish(OutboxEvent event) {
        BankingEvent payload = deserialize(event);
        rabbit.invoke(operations -> {
            operations.convertAndSend(RabbitMqConfig.BANKING_EXCHANGE, event.getRoutingKey(), payload);
            operations.waitForConfirmsOrDie(properties.confirmTimeout().toMillis());
            return null;
        });
    }

    private BankingEvent deserialize(OutboxEvent event) {
        try {
            return json.readValue(event.getPayload(), BankingEvent.class);
        } catch (JacksonException invalidPayload) {
            throw new IllegalStateException("Outbox event " + event.getId() + " contains invalid JSON", invalidPayload);
        }
    }
}
