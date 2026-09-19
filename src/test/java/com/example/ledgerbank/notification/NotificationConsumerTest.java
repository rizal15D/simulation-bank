package com.example.ledgerbank.notification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.ledgerbank.event.BankingEvent;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class NotificationConsumerTest {
    private final NotificationRepository notifications = Mockito.mock(NotificationRepository.class);
    private final NotificationConsumer consumer = new NotificationConsumer(notifications);

    @Test
    void transferCompletedCreatesOneNotificationPerDistinctCustomer() {
        UUID sourceCustomer = UUID.randomUUID();
        UUID destinationCustomer = UUID.randomUUID();
        BankingEvent event = BankingEvent.transferCompleted(UUID.randomUUID(), sourceCustomer,
                destinationCustomer, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("25.00"));

        consumer.consume(event);

        verify(notifications, times(2)).insertIfAbsent(any(), eq(event.eventId()), any(),
                eq("TRANSFER_COMPLETED"), eq("Transfer completed"), anyString(), any());
        verify(notifications).insertIfAbsent(any(), eq(event.eventId()), eq(sourceCustomer),
                anyString(), anyString(), anyString(), any());
        verify(notifications).insertIfAbsent(any(), eq(event.eventId()), eq(destinationCustomer),
                anyString(), anyString(), anyString(), any());
    }

    @Test
    void nonCompletedTransferEventIsIgnored() {
        BankingEvent event = BankingEvent.transferFailed(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "INSUFFICIENT_BALANCE");

        consumer.consume(event);

        verify(notifications, never()).insertIfAbsent(any(), any(), any(), anyString(),
                anyString(), anyString(), any());
    }
}
