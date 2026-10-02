package com.example.ledgerbank.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ledgerbank.common.config.RabbitMqConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.ObjectMapper;

class ConfirmedRabbitEventPublisherTest {
    private final RabbitTemplate rabbit = Mockito.mock(RabbitTemplate.class);
    private final RabbitOperations operations = Mockito.mock(RabbitOperations.class);
    private final ObjectMapper json = Mockito.mock(ObjectMapper.class);
    private final OutboxProperties properties = new OutboxProperties(50, 8, Duration.ofSeconds(1),
            Duration.ofMinutes(1), Duration.ofSeconds(2), Duration.ofSeconds(30), Duration.ofDays(7));
    private final ConfirmedRabbitEventPublisher publisher =
            new ConfirmedRabbitEventPublisher(rabbit, json, properties);

    @Test
    void eventIsMarkedForDeliveryOnlyAfterBrokerConfirm() throws Exception {
        BankingEvent payload = BankingEvent.userLogin(UUID.randomUUID(), UUID.randomUUID());
        OutboxEvent event = new OutboxEvent(payload, "payload", Instant.now());
        when(json.readValue("payload", BankingEvent.class)).thenReturn(payload);
        when(rabbit.invoke(any())).thenAnswer(invocation -> {
            RabbitOperations.OperationsCallback<?> callback = invocation.getArgument(0);
            return callback.doInRabbit(operations);
        });

        publisher.publish(event);

        verify(operations).convertAndSend(RabbitMqConfig.BANKING_EXCHANGE, "user.login", payload);
        verify(operations).waitForConfirmsOrDie(2_000L);
    }
}
