package com.example.ledgerbank.event;

import static org.mockito.Mockito.verify;

import com.example.ledgerbank.common.config.RabbitMqConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.mockito.Mockito;

class RabbitBankingEventPublisherTest {
    private final RabbitTemplate rabbit = Mockito.mock(RabbitTemplate.class);
    private final RabbitBankingEventPublisher publisher = new RabbitBankingEventPublisher(rabbit);

    @Test
    void eventUsesDurableExchangeAndTypeRoutingKey() {
        BankingEvent event = BankingEvent.userLogin(UUID.randomUUID(), UUID.randomUUID());

        publisher.publish(event);

        verify(rabbit).convertAndSend(RabbitMqConfig.BANKING_EXCHANGE, "user.login", event);
    }
}
