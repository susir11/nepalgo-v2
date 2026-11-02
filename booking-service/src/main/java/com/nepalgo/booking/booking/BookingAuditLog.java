package com.nepalgo.booking.booking;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "booking_audit_log")
@Getter
@Setter
@NoArgsConstructor
public class BookingAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id")
    private Long adminUserId;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "customer_id")
    private Long customerId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String details;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}