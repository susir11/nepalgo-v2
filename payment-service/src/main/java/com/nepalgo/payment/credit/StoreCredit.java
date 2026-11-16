package com.nepalgo.payment.credit;

import com.nepalgo.payment.bill.InvoiceNumberService;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// A NepalGo Travel Credit: a spendable, expiring promise of value in USD.
// Life cycle:  ACTIVE --apply to trip--> RESERVED --booking paid--> REDEEMED
//                 ^------- released (booking withdrawn / credit rejected) --'
//              ACTIVE --admin--> VOID
// Like a remittance "hold": the money isn't gone when the customer picks the
// credit, it's earmarked; it's only consumed when the transaction settles.
@Entity
@Table(name = "store_credits")
@Getter
@Setter
@NoArgsConstructor
public class StoreCredit {

    public enum Status { ACTIVE, RESERVED, REDEEMED, VOID }
    public enum Source { CANCELLATION, MANUAL }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "amount_usd", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountUsd;

    @Column(name = "expires_on", nullable = false)
    private LocalDate expiresOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source;

    @Column(name = "source_booking_id")
    private Long sourceBookingId;

    @Column(name = "reserved_booking_id")
    private Long reservedBookingId;

    // Admin's decision while pricing the bill: null = undecided, true = netted, false = rejected.
    private Boolean accepted;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    @Column(name = "void_reason", columnDefinition = "TEXT")
    private String voidReason;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(name = "issued_by")
    private Long issuedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    // Expiry is derived, not stored: no nightly job can forget to run.
    public boolean isExpired() {
        return expiresOn.isBefore(InvoiceNumberService.today());
    }

    public boolean isSpendable() {
        return status == Status.ACTIVE && !isExpired();
    }

    // What the wallet card / ledger shows.
    public String displayStatus() {
        return status == Status.ACTIVE && isExpired() ? "EXPIRED" : status.name();
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
