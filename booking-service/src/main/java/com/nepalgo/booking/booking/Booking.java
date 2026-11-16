package com.nepalgo.booking.booking;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "voucher_code", nullable = false, unique = true, length = 16)
    private String voucherCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BookingStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingOrigin origin;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "destination_id", nullable = false)
    private Long destinationId;

    @Column(name = "destination_name_snapshot", nullable = false)
    private String destinationNameSnapshot;

    @Column(name = "itinerary_version_id", nullable = false)
    private Long itineraryVersionId;

    // JdbcTypeCode(SqlTypes.JSON) tells Hibernate to treat this as
    // Postgres JSONB, storing/retrieving it as a raw JSON string — same
    // category of "tell Hibernate the real column type" fix as the
    // tsvector columnDefinition issue from catalog-service.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "itinerary_snapshot", nullable = false, columnDefinition = "jsonb")
    private String itinerarySnapshot;

    @Column(name = "party_size", nullable = false)
    private int partySize;

    @Column(name = "arrival_date", nullable = false)
    private LocalDate arrivalDate;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(name = "guide_requested", nullable = false)
    private boolean guideRequested = false;

    @Column(name = "porter_requested", nullable = false)
    private boolean porterRequested = false;

    @Column(name = "special_request_text", columnDefinition = "TEXT")
    private String specialRequestText;

    @Column(name = "lead_booker_legal_name")
    private String leadBookerLegalName;

    @Column(name = "lead_booker_passport_number")
    private String leadBookerPassportNumber;

    @Column(name = "lead_booker_email")
    private String leadBookerEmail;

    // Read-only copy of the bill's invoice number (payment-service is the source
    // of truth). Non-null = the bill has been issued, so the customer can pay.
    @Column(name = "invoice_number", length = 50)
    private String invoiceNumber;

    // Id of the receipt row in payment-service (which owns the private image).
    @Column(name = "payment_receipt_id")
    private Long paymentReceiptId;

    @Column(name = "payment_receipt_code", length = 100)
    private String paymentReceiptCode;

    // (consent_confirmed placeholder removed in V5: legal-service owns ConsentRecord)

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "internal_notes", columnDefinition = "TEXT")
    private String internalNotes;

    @Column(name = "cancellation_tier", length = 20)
    private String cancellationTier;

    @Column(name = "cancellation_refund_percent", precision = 5, scale = 2)
    private BigDecimal cancellationRefundPercent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "awaiting_payment_since")
    private Instant awaitingPaymentSince;

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