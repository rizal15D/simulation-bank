package com.example.ledgerbank.transfer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "transfers")
public class Transfer {
    @Id
    private UUID id;

    @Column(name = "reference_number", nullable = false, unique = true, updatable = false, length = 40)
    private String referenceNumber;

    @Column(name = "source_account_id", nullable = false, updatable = false)
    private UUID sourceAccountId;

    @Column(name = "destination_account_id", nullable = false, updatable = false)
    private UUID destinationAccountId;

    @Column(nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(updatable = false, length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private TransferStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;

    protected Transfer() {
    }

    public Transfer(UUID sourceAccountId, UUID destinationAccountId, BigDecimal amount, String description) {
        this.id = UUID.randomUUID();
        this.referenceNumber = "TRF-" + id;
        this.sourceAccountId = sourceAccountId;
        this.destinationAccountId = destinationAccountId;
        this.amount = amount;
        this.description = description;
        this.status = TransferStatus.SUCCESS;
        this.createdAt = Instant.now();
        this.completedAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getReferenceNumber() { return referenceNumber; }
    public UUID getSourceAccountId() { return sourceAccountId; }
    public UUID getDestinationAccountId() { return destinationAccountId; }
    public BigDecimal getAmount() { return amount; }
    public String getDescription() { return description; }
    public TransferStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
