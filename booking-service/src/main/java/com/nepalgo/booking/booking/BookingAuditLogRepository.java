package com.nepalgo.booking.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BookingAuditLogRepository extends JpaRepository<BookingAuditLog, Long> {
    List<BookingAuditLog> findByBookingIdOrderByCreatedAt(Long bookingId);
}