package com.nepalgo.payment.credit;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

// Small companion entities, grouped to keep the file count down.
public final class CreditEntities {

    private CreditEntities() {}

    // One row per admin edit of a credit (amount, expiry, void...).
    @Entity
    @Table(name = "store_credit_amendments")
    @Getter
    @Setter
    @NoArgsConstructor
    public static class StoreCreditAmendment {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "credit_id", nullable = false)
        private Long creditId;

        @Column(name = "admin_user_id", nullable = false)
        private Long adminUserId;

        @Column(name = "field_changed", nullable = false, length = 50)
        private String fieldChanged;

        @Column(name = "old_value", columnDefinition = "TEXT")
        private String oldValue;

        @Column(name = "new_value", columnDefinition = "TEXT")
        private String newValue;

        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;

        @PrePersist
        void onCreate() { createdAt = Instant.now(); }
    }

    // A cash refund owed to a customer, worked manually from the admin queue.
    @Entity
    @Table(name = "refund_requests")
    @Getter
    @Setter
    @NoArgsConstructor
    public static class RefundRequest {
        public enum Status { PENDING, COMPLETED }

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "booking_id", nullable = false, unique = true)
        private Long bookingId;

        @Column(name = "customer_id", nullable = false)
        private Long customerId;

        @Column(name = "bill_id", nullable = false)
        private Long billId;

        @Column(nullable = false, length = 20)
        private String tier;

        @Column(name = "refund_percent", nullable = false, precision = 5, scale = 2)
        private BigDecimal refundPercent;

        @Column(name = "amount_usd", nullable = false, precision = 12, scale = 2)
        private BigDecimal amountUsd;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false, length = 10)
        private Status status;

        @Column(name = "payout_reference", length = 100)
        private String payoutReference;

        @Column(name = "completed_by")
        private Long completedBy;

        @Column(name = "completed_at")
        private Instant completedAt;

        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;

        @PrePersist
        void onCreate() { createdAt = Instant.now(); }
    }
}
