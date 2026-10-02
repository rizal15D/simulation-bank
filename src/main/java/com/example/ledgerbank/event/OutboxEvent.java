package com.example.ledgerbank.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    private static final int MAX_ERROR_LENGTH = 500;

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false, updatable = false, length = 60)
    private String eventType;

    @Column(name = "routing_key", nullable = false, updatable = false, length = 100)
    private String routingKey;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    protected OutboxEvent() {
    }

    public OutboxEvent(BankingEvent event, String payload, Instant createdAt) {
        Objects.requireNonNull(event, "event");
        this.id = event.eventId();
        this.eventType = event.eventType().name();
        this.routingKey = event.eventType().routingKey();
        this.payload = Objects.requireNonNull(payload, "payload");
        this.status = OutboxStatus.PENDING;
        this.attemptCount = 0;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.nextAttemptAt = createdAt;
    }

    public void markPublished(Instant publishedAt) {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt");
        this.lastError = null;
    }

    public void claimUntil(Instant claimDeadline) {
        if (status != OutboxStatus.PENDING) {
            throw new IllegalStateException("Only pending outbox events can be claimed");
        }
        this.nextAttemptAt = Objects.requireNonNull(claimDeadline, "claimDeadline");
    }

    public boolean isClaimedUntil(Instant claimDeadline) {
        return status == OutboxStatus.PENDING && nextAttemptAt.equals(claimDeadline);
    }

    public void recordFailure(Instant retryAt, String error, boolean exhausted) {
        this.attemptCount++;
        this.status = exhausted ? OutboxStatus.DEAD : OutboxStatus.PENDING;
        this.nextAttemptAt = Objects.requireNonNull(retryAt, "retryAt");
        this.lastError = abbreviate(error);
    }

    private String abbreviate(String error) {
        String message = error == null || error.isBlank() ? "Unknown publish failure" : error;
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }

    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public String getRoutingKey() { return routingKey; }
    public String getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public String getLastError() { return lastError; }
}
