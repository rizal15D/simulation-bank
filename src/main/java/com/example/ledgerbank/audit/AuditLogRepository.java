package com.example.ledgerbank.audit;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO audit_logs
                (id, event_id, actor_id, action, resource_type, resource_id, metadata, created_at)
            VALUES
                (:id, :eventId, :actorId, :action, :resourceType, :resourceId, :metadata, :createdAt)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("eventId") UUID eventId,
                       @Param("actorId") UUID actorId,
                       @Param("action") String action,
                       @Param("resourceType") String resourceType,
                       @Param("resourceId") UUID resourceId,
                       @Param("metadata") String metadata,
                       @Param("createdAt") Instant createdAt);
}
