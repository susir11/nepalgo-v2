package com.nepalgo.notification.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    // Claim due deliveries. FOR UPDATE SKIP LOCKED: if two instances of this
    // service run, each grabs DIFFERENT rows instead of double-sending — the
    // classic job-queue-in-Postgres pattern (no extra broker needed).
    @Query(value = """
            SELECT * FROM deliveries
            WHERE status = 'PENDING' AND next_attempt_at <= now()
            ORDER BY next_attempt_at
            LIMIT :batch
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Delivery> claimDue(@Param("batch") int batch);

    List<Delivery> findByNotificationIdOrderById(Long notificationId);
}
