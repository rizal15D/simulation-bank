package com.example.ledgerbank.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ledgerbank.auth.BankingPrincipal;
import com.example.ledgerbank.auth.UserRole;
import com.example.ledgerbank.common.exception.BusinessException;
import com.example.ledgerbank.common.observability.BankingMetrics;
import com.example.ledgerbank.event.BankingEvent;
import com.example.ledgerbank.event.BankingEventPublisher;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class IdempotentTransferServiceTest {
    @Mock private RedisTransferIdempotencyStore redis;
    @Mock private TransferIdempotencyRepository records;
    @Mock private TransferRepository transfers;
    @Mock private TransferService core;
    @Mock private TransactionTemplate transaction;
    @Mock private BankingEventPublisher events;
    private IdempotentTransferService service;

    private final BankingPrincipal actor = new BankingPrincipal(UUID.randomUUID(), UUID.randomUUID(),
            "owner@example.com", UserRole.CUSTOMER);
    private final UUID source = UUID.randomUUID();
    private final UUID destination = UUID.randomUUID();
    private final TransferRequest request = new TransferRequest(source, destination, new BigDecimal("25.00"), "Lunch");

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new IdempotentTransferService(redis, records, transfers, core, transaction, events,
                new BankingMetrics(new SimpleMeterRegistry()),
                new TransferRequestValidator(),
                Duration.ofHours(24), Duration.ofSeconds(30));
        lenient().when(transaction.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
    }

    @Test
    void retryReturnsOriginalTransferWithoutMovingMoneyTwice() {
        String key = "transfer-key-001";
        Transfer transfer = new Transfer(source, destination, new BigDecimal("25.00"), "Lunch");
        TransferResponse created = TransferResponse.from(transfer);
        when(redis.get(actor.userId(), key)).thenReturn(Optional.empty());
        when(redis.acquire(eq(actor.userId()), eq(key), any(), eq(Duration.ofSeconds(30)))).thenReturn(true);
        when(records.findByActorIdAndIdempotencyKey(actor.userId(), key)).thenReturn(Optional.empty());
        when(core.transfer(actor, source, destination, new BigDecimal("25.00"), "Lunch")).thenReturn(created);

        assertThat(service.transfer(actor, key, request)).isEqualTo(created);

        ArgumentCaptor<String> requestHash = ArgumentCaptor.forClass(String.class);
        verify(redis).put(eq(actor.userId()), eq(key), requestHash.capture(), eq(created.id()),
                eq(Duration.ofHours(24)));
        when(redis.get(actor.userId(), key)).thenReturn(Optional.of(
                new RedisTransferIdempotencyStore.CachedTransfer(requestHash.getValue(), created.id())));
        when(transfers.findById(created.id())).thenReturn(Optional.of(transfer));

        assertThat(service.transfer(actor, key, request)).isEqualTo(created);
        verify(core, times(1)).transfer(actor, source, destination, new BigDecimal("25.00"), "Lunch");
    }

    @Test
    void sameKeyCannotBeReusedForDifferentRequest() {
        String key = "transfer-key-002";
        when(redis.get(actor.userId(), key)).thenReturn(Optional.of(
                new RedisTransferIdempotencyStore.CachedTransfer("different-request-hash", UUID.randomUUID())));

        assertThatThrownBy(() -> service.transfer(actor, key, request))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void concurrentDuplicateIsRejectedBeforeTransferExecutes() {
        String key = "transfer-key-003";
        when(redis.get(actor.userId(), key)).thenReturn(Optional.empty());
        when(redis.acquire(eq(actor.userId()), eq(key), any(), eq(Duration.ofSeconds(30)))).thenReturn(false);
        when(records.findByActorIdAndIdempotencyKey(actor.userId(), key)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transfer(actor, key, request))
                .isInstanceOf(BusinessException.class).extracting("code")
                .isEqualTo("IDEMPOTENCY_REQUEST_IN_PROGRESS");
    }

    @Test
    void failedCoreTransferPublishesSanitizedFailureEvent() {
        String key = "transfer-key-004";
        when(redis.get(actor.userId(), key)).thenReturn(Optional.empty());
        when(redis.acquire(eq(actor.userId()), eq(key), any(), eq(Duration.ofSeconds(30)))).thenReturn(true);
        when(records.findByActorIdAndIdempotencyKey(actor.userId(), key)).thenReturn(Optional.empty());
        when(core.transfer(actor, source, destination, new BigDecimal("25.00"), "Lunch"))
                .thenThrow(new BusinessException("INSUFFICIENT_BALANCE", "insufficient", org.springframework.http.HttpStatus.CONFLICT));

        assertThatThrownBy(() -> service.transfer(actor, key, request))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("INSUFFICIENT_BALANCE");

        ArgumentCaptor<BankingEvent> event = ArgumentCaptor.forClass(BankingEvent.class);
        verify(events).publish(event.capture());
        assertThat(event.getValue().eventType().name()).isEqualTo("TRANSFER_FAILED");
        assertThat(event.getValue().metadata()).containsEntry("failureCode", "INSUFFICIENT_BALANCE");
    }
}
