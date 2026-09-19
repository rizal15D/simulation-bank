package com.example.ledgerbank.event;

import com.example.ledgerbank.common.config.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RabbitBankingEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(RabbitBankingEventPublisher.class);

    private final RabbitTemplate rabbit;

    public RabbitBankingEventPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void publish(BankingEvent event) {
        try {
            rabbit.convertAndSend(RabbitMqConfig.BANKING_EXCHANGE, event.eventType().routingKey(), event);
        } catch (AmqpException unavailable) {
            log.error("Banking event {} ({}) could not be published", event.eventId(), event.eventType(), unavailable);
        }
    }
}
