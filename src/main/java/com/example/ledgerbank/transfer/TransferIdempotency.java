package com.example.ledgerbank.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfer_idempotency")
public class TransferIdempotency {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "transfer_id", nullable = false, unique = true)
    private UUID transferId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    protected TransferIdempotency() {}

    public TransferIdempotency(UUID actorId, String idempotencyKey, String requestHash,
                               UUID transferId, Instant expiresAt) {
        this.actorId = actorId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.transferId = transferId;
        this.createdAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public UUID getActorId() { return actorId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestHash() { return requestHash; }
    public UUID getTransferId() { return transferId; }
    public Instant getExpiresAt() { return expiresAt; }
}
