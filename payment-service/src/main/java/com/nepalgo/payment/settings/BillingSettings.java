package com.nepalgo.payment.settings;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "billing_settings")
@Getter
@Setter
@NoArgsConstructor
public class BillingSettings {

    // Single-row table: id is always 1 (a CHECK constraint enforces it).
    @Id
    private Integer id;

    @Column(name = "vat_registered", nullable = false)
    private boolean vatRegistered;

    @Column(name = "vat_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatPercent;

    @Column(name = "service_charge_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal serviceChargePercent;

    @Column(name = "seller_legal_name", nullable = false)
    private String sellerLegalName;

    @Column(name = "seller_pan_vat_number", nullable = false, length = 50)
    private String sellerPanVatNumber;

    @Column(name = "seller_address", nullable = false, columnDefinition = "TEXT")
    private String sellerAddress;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}