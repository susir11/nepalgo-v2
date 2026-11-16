package com.nepalgo.payment.fx;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "fx_rates")
@Getter
@Setter
@NoArgsConstructor
public class FxRate {

    // PENDING = proposed by the maker, APPROVED = the current rate,
    // RETIRED = was current once, superseded by a newer approval.
    public enum Status { PENDING, APPROVED, RETIRED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "currency_pair", nullable = false, length = 10)
    private String currencyPair;

    @Column(nullable = false, precision = 14, scale = 6)
    private BigDecimal rate;

    @Column(name = "proposed_by", nullable = false)
    private Long proposedBy;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "effective_from")
    private Instant effectiveFrom;

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