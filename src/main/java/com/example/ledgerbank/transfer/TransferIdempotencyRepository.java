package com.example.ledgerbank.transfer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferIdempotencyRepository extends JpaRepository<TransferIdempotency, UUID> {
    Optional<TransferIdempotency> findByActorIdAndIdempotencyKey(UUID actorId, String idempotencyKey);
}
