package com.nepalgo.payment.bill;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// The two history records of a bill, kept in one file because they are small
// and always used together:
//  - BillAmendment  = "edited while unpaid" (same invoice number, logged)
//  - BillCorrection = "fixed after lock"   (a NEW bill with a NEW number, linked)
public final class BillHistory {

    private BillHistory() {}

    @Entity
    @Table(name = "bill_amendments")
    @Getter
    @Setter
    @NoArgsConstructor
    public static class BillAmendment {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "bill_id", nullable = false)
        private Long billId;

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

    @Entity
    @Table(name = "bill_corrections")
    @Getter
    @Setter
    @NoArgsConstructor
    public static class BillCorrection {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "original_bill_id", nullable = false)
        private Long originalBillId;

        @Column(name = "new_bill_id", nullable = false, unique = true)
        private Long newBillId;

        @Column(nullable = false, columnDefinition = "TEXT")
        private String reason;

        @Column(name = "created_by", nullable = false)
        private Long createdBy;

        @Column(name = "created_at", nullable = false, updatable = false)
        private Instant createdAt;

        @PrePersist
        void onCreate() { createdAt = Instant.now(); }
    }
}
