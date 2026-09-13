package com.example.ledgerbank.transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(UUID id, String referenceNumber, UUID sourceAccountId, UUID destinationAccountId,
                               BigDecimal amount, String description, TransferStatus status,
                               Instant createdAt, Instant completedAt) {
    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(transfer.getId(), transfer.getReferenceNumber(), transfer.getSourceAccountId(),
                transfer.getDestinationAccountId(), transfer.getAmount(), transfer.getDescription(), transfer.getStatus(),
                transfer.getCreatedAt(), transfer.getCompletedAt());
    }
}
