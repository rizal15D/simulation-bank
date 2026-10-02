package com.example.ledgerbank.event;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("ledgerbank.outbox")
public record OutboxProperties(
        int batchSize,
        int maxAttempts,
        Duration initialBackoff,
        Duration maxBackoff,
        Duration confirmTimeout,
        Duration claimLease,
        Duration retention) {

    public OutboxProperties {
        if (batchSize < 1) {
            throw new IllegalArgumentException("ledgerbank.outbox.batch-size must be positive");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("ledgerbank.outbox.max-attempts must be positive");
        }
        initialBackoff = positive(initialBackoff, "initial-backoff");
        maxBackoff = positive(maxBackoff, "max-backoff");
        confirmTimeout = positive(confirmTimeout, "confirm-timeout");
        claimLease = positive(claimLease, "claim-lease");
        retention = positive(retention, "retention");
        if (maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("ledgerbank.outbox.max-backoff must not be shorter than initial-backoff");
        }
        if (claimLease.compareTo(confirmTimeout) < 0) {
            throw new IllegalArgumentException("ledgerbank.outbox.claim-lease must not be shorter than confirm-timeout");
        }
    }

    private static Duration positive(Duration value, String property) {
        Objects.requireNonNull(value, "ledgerbank.outbox." + property + " is required");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("ledgerbank.outbox." + property + " must be positive");
        }
        return value;
    }
}
