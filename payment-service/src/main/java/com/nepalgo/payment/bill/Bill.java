package com.nepalgo.payment.bill;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// Bill header (spec section 21). Think of it as a remittance transaction
// record: the rate, fees and parties are frozen onto the record itself, so the
// record still reads correctly years later even if the "live" settings change.
@Entity
@Table(name = "bills")
@Getter
@Setter
@NoArgsConstructor
public class Bill {

    // DRAFT  = unpaid, editable (every edit logged as a BillAmendment)
    // LOCKED = customer paid + admin verified; never edited again, only corrected
    public enum Status { DRAFT, LOCKED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "invoice_number", length = 40, unique = true)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "seller_legal_name", nullable = false)
    private String sellerLegalName;

    @Column(name = "seller_pan_vat_number", nullable = false, length = 50)
    private String sellerPanVatNumber;

    @Column(name = "seller_address", nullable = false, columnDefinition = "TEXT")
    private String sellerAddress;

    @Column(name = "buyer_name")
    private String buyerName;

    @Column(name = "buyer_pan", length = 50)
    private String buyerPan;

    @Column(name = "currency_pair", nullable = false, length = 10)
    private String currencyPair;

    @Column(name = "fx_rate", nullable = false, precision = 14, scale = 6)
    private BigDecimal fxRate;

    @Column(name = "vat_registered", nullable = false)
    private boolean vatRegistered;

    @Column(name = "vat_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal vatPercent;

    @Column(name = "service_charge_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal serviceChargePercent;

    @Column(name = "applied_credit_amount", precision = 12, scale = 2)
    private BigDecimal appliedCreditAmount;

    @Column(name = "payoneer_link", length = 1000)
    private String payoneerLink;

    @Column(nullable = false)
    private boolean superseded;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Optimistic locking: if two admins load the same bill and both save, the
    // second save fails (409) instead of silently overwriting the first —
    // like rejecting a payout instruction built on a stale account balance.
    @Version
    private Long version;

    // orphanRemoval: replacing the list deletes the old rows (the edit form
    // always sends the full desired list of lines).
    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<BillLineItem> lineItems = new ArrayList<>();

    public boolean isIssued() {
        return invoiceNumber != null;
    }

    public void replaceLineItems(List<BillLineItem> newItems) {
        lineItems.clear();
        int pos = 1;
        for (BillLineItem item : newItems) {
            item.setBill(this);
            item.setPosition(pos++);
            lineItems.add(item);
        }
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
