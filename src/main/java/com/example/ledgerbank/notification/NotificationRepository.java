package com.example.ledgerbank.notification;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    @Modifying
    @Query(value = """
            INSERT INTO notifications
                (id, event_id, customer_id, event_type, title, message, created_at)
            VALUES
                (:id, :eventId, :customerId, :eventType, :title, :message, :createdAt)
            ON CONFLICT (event_id, customer_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("eventId") UUID eventId,
                       @Param("customerId") UUID customerId,
                       @Param("eventType") String eventType,
                       @Param("title") String title,
                       @Param("message") String message,
                       @Param("createdAt") Instant createdAt);
}
