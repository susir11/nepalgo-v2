package com.nepalgo.auth.customer;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "customers")
@Getter
@Setter
@NoArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "firebase_uid", nullable = false, unique = true)
    private String firebaseUid;

    // Stored raw (no spaces, no dashes) — the "4829 1057" grouped display
    // format from section 5a is a presentation concern, applied in the DTO,
    // never in the database.
    @Column(name = "nepalgo_id", nullable = false, unique = true, length = 8)
    private String nepalgoId;

    private String email;

    @Column(name = "display_name")
    private String displayName;

    // ACTIVE | SUSPENDED (spec 15.2). Suspended = no NEW bookings or payments;
    // existing trips, history and support stay available.
    @Column(nullable = false, length = 12)
    private String status = "ACTIVE";

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "suspension_reason", columnDefinition = "TEXT")
    private String suspensionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}