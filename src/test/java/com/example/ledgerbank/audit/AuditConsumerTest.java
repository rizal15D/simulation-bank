package com.example.ledgerbank.audit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ledgerbank.event.BankingEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

class AuditConsumerTest {
    private final AuditLogRepository auditLogs = Mockito.mock(AuditLogRepository.class);
    private final ObjectMapper json = Mockito.mock(ObjectMapper.class);
    private final AuditConsumer consumer = new AuditConsumer(auditLogs, json);

    @Test
    void eventIsPersistedWithoutSensitivePayload() throws Exception {
        BankingEvent event = BankingEvent.userLogin(UUID.randomUUID(), UUID.randomUUID());
        when(json.writeValueAsString(event.metadata())).thenReturn("{}");

        consumer.consume(event);

        verify(auditLogs).insertIfAbsent(any(), eq(event.eventId()), eq(event.actorId()),
                eq("USER_LOGIN"), eq("USER"), eq(event.resourceId()), eq("{}"), eq(event.occurredAt()));
    }
}
