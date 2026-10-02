package com.example.ledgerbank.event;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query(value = """
            SELECT * FROM outbox_events
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY next_attempt_at, created_at
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockReadyBatch(@Param("now") Instant now, @Param("batchSize") int batchSize);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT event FROM OutboxEvent event WHERE event.id = :id")
    Optional<OutboxEvent> lockById(@Param("id") UUID id);

    long countByStatus(OutboxStatus status);

    @Modifying
    @Query("DELETE FROM OutboxEvent event WHERE event.status = 'PUBLISHED' AND event.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);
}
