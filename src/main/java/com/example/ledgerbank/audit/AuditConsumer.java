package com.example.ledgerbank.audit;

import com.example.ledgerbank.common.config.RabbitMqConfig;
import com.example.ledgerbank.event.BankingEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
public class AuditConsumer {
    private final AuditLogRepository auditLogs;
    private final ObjectMapper json;

    public AuditConsumer(AuditLogRepository auditLogs, ObjectMapper json) {
        this.auditLogs = auditLogs;
        this.json = json;
    }

    @RabbitListener(queues = RabbitMqConfig.AUDIT_QUEUE)
    @Transactional
    public void consume(BankingEvent event) {
        auditLogs.insertIfAbsent(UUID.randomUUID(), event.eventId(), event.actorId(), event.eventType().name(),
                event.resourceType(), event.resourceId(), metadata(event), event.occurredAt());
    }

    private String metadata(BankingEvent event) {
        try {
            return json.writeValueAsString(event.metadata());
        } catch (JacksonException invalidMetadata) {
            throw new IllegalStateException("Banking event metadata could not be serialized", invalidMetadata);
        }
    }
}
