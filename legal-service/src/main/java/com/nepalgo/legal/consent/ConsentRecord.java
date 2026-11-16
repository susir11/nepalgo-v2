package com.nepalgo.legal.consent;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// Evidence that a customer agreed to an exact Terms version for a booking,
// card by card (spec 9). Append-only: a DB trigger rejects UPDATE/DELETE, and
// every field is updatable = false so Hibernate never even tries.
@Entity
@Table(name = "consent_records")
@Getter
@Setter
@NoArgsConstructor
public class ConsentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private Long customerId;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private Long bookingId;

    @Column(name = "legal_document_id", nullable = false, updatable = false)
    private Long legalDocumentId;

    @Column(name = "version_label", nullable = false, updatable = false, length = 20)
    private String versionLabel;

    @Column(name = "content_sha256", nullable = false, updatable = false, length = 64)
    private String contentSha256;

    @Column(name = "cancellation_expanded_at", nullable = false, updatable = false)
    private Instant cancellationExpandedAt;

    @Column(name = "cancellation_checked_at", nullable = false, updatable = false)
    private Instant cancellationCheckedAt;

    @Column(name = "force_majeure_expanded_at", nullable = false, updatable = false)
    private Instant forceMajeureExpandedAt;

    @Column(name = "force_majeure_checked_at", nullable = false, updatable = false)
    private Instant forceMajeureCheckedAt;

    @Column(name = "identity_expanded_at", nullable = false, updatable = false)
    private Instant identityExpandedAt;

    @Column(name = "identity_checked_at", nullable = false, updatable = false)
    private Instant identityCheckedAt;

    @Column(name = "consented_at", nullable = false, updatable = false)
    private Instant consentedAt;

    @Column(name = "client_ip", updatable = false, length = 64)
    private String clientIp;

    @Column(name = "user_agent", updatable = false, length = 500)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
