package com.example.ledgerbank.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record BankingEvent(
        UUID eventId,
        BankingEventType eventType,
        UUID actorId,
        UUID sourceCustomerId,
        UUID destinationCustomerId,
        String resourceType,
        UUID resourceId,
        Map<String, String> metadata,
        Instant occurredAt) {

    public BankingEvent {
        eventId = Objects.requireNonNull(eventId, "eventId");
        eventType = Objects.requireNonNull(eventType, "eventType");
        resourceType = Objects.requireNonNull(resourceType, "resourceType");
        resourceId = Objects.requireNonNull(resourceId, "resourceId");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public static BankingEvent userLogin(UUID actorId, UUID customerId) {
        return new BankingEvent(UUID.randomUUID(), BankingEventType.USER_LOGIN, actorId,
                customerId, null, "USER", actorId, Map.of(), Instant.now());
    }

    public static BankingEvent accountCreated(UUID actorId, UUID customerId, UUID accountId, String currency) {
        return new BankingEvent(UUID.randomUUID(), BankingEventType.ACCOUNT_CREATED, actorId,
                customerId, null, "ACCOUNT", accountId, Map.of("currency", currency), Instant.now());
    }

    public static BankingEvent transferCompleted(UUID actorId, UUID sourceCustomerId, UUID destinationCustomerId,
                                                  UUID transferId, UUID sourceAccountId,
                                                  UUID destinationAccountId, BigDecimal amount) {
        return new BankingEvent(UUID.randomUUID(), BankingEventType.TRANSFER_COMPLETED, actorId,
                sourceCustomerId, destinationCustomerId, "TRANSFER", transferId,
                Map.of("sourceAccountId", sourceAccountId.toString(),
                        "destinationAccountId", destinationAccountId.toString(),
                        "amount", amount.toPlainString()), Instant.now());
    }

    public static BankingEvent transferFailed(UUID actorId, UUID sourceAccountId,
                                               UUID destinationAccountId, String failureCode) {
        UUID resourceId = sourceAccountId != null ? sourceAccountId : actorId;
        if (resourceId == null) {
            resourceId = UUID.randomUUID();
        }
        Map<String, String> details = destinationAccountId == null
                ? Map.of("failureCode", failureCode)
                : Map.of("failureCode", failureCode,
                        "destinationAccountId", destinationAccountId.toString());
        return new BankingEvent(UUID.randomUUID(), BankingEventType.TRANSFER_FAILED, actorId,
                null, null, "TRANSFER_REQUEST", resourceId, details, Instant.now());
    }
}
