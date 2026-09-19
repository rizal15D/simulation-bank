package com.example.ledgerbank.notification;

import com.example.ledgerbank.common.config.RabbitMqConfig;
import com.example.ledgerbank.event.BankingEvent;
import com.example.ledgerbank.event.BankingEventType;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationConsumer {
    private final NotificationRepository notifications;

    public NotificationConsumer(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @RabbitListener(queues = RabbitMqConfig.NOTIFICATION_QUEUE)
    @Transactional
    public void consume(BankingEvent event) {
        if (event.eventType() != BankingEventType.TRANSFER_COMPLETED) {
            return;
        }
        Set<UUID> recipients = new LinkedHashSet<>();
        if (event.sourceCustomerId() != null) {
            recipients.add(event.sourceCustomerId());
        }
        if (event.destinationCustomerId() != null) {
            recipients.add(event.destinationCustomerId());
        }
        Instant createdAt = Instant.now();
        for (UUID customerId : recipients) {
            notifications.insertIfAbsent(UUID.randomUUID(), event.eventId(), customerId,
                    event.eventType().name(), "Transfer completed",
                    "Transfer " + event.resourceId() + " completed successfully", createdAt);
        }
    }
}
