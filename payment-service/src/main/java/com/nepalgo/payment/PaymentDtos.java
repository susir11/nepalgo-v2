package com.nepalgo.payment;

import com.nepalgo.payment.bill.BillCalculator;
import com.nepalgo.payment.bill.BillLineItem;
import com.nepalgo.payment.fx.FxRate;
import com.nepalgo.payment.settings.BillingSettings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// All request/response shapes for payment-service live here as nested records
// (later steps add more). Grouped in one file on purpose: fewer files to create.
public final class PaymentDtos {

    private PaymentDtos() {}

    public record ProposeFxRateRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z]{3}/[A-Za-z]{3}$", message = "must look like USD/NPR") String currencyPair,
            @NotNull @DecimalMin(value = "0.000001") BigDecimal rate
    ) {}

    public record FxRateResponse(Long id, String currencyPair, BigDecimal rate, String status,
                                 Long proposedBy, Long approvedBy, Instant effectiveFrom, Instant createdAt) {
        public static FxRateResponse from(FxRate f) {
            return new FxRateResponse(f.getId(), f.getCurrencyPair(), f.getRate(), f.getStatus().name(),
                    f.getProposedBy(), f.getApprovedBy(), f.getEffectiveFrom(), f.getCreatedAt());
        }
    }

    // ===================== Bills =====================

    public record LineItemInput(
            @NotBlank @Size(max = 500) String description,
            @NotNull BillLineItem.Category category,
            @Min(1) Integer itineraryDay,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2) BigDecimal quantity,
            @NotNull @DecimalMin(value = "0.00") @Digits(integer = 12, fraction = 2) BigDecimal unitPriceNpr
    ) {}

    public record CreateBillRequest(
            @NotNull Long bookingId,
            @Size(max = 50) String buyerPan,
            @Pattern(regexp = "^https://.+", message = "must be an https:// link") @Size(max = 1000) String payoneerLink,
            @NotNull @Valid List<LineItemInput> lineItems
    ) {}

    // The ONE edit form (spec: "one Edit button, not two admin concepts").
    // Carries the full desired state; the server decides amendment vs correction.
    // reason is required only when the bill is LOCKED (it becomes the correction reason).
    public record UpdateBillRequest(
            @Size(max = 50) String buyerPan,
            @Pattern(regexp = "^https://.+", message = "must be an https:// link") @Size(max = 1000) String payoneerLink,
            @NotNull @Valid List<LineItemInput> lineItems,
            @Size(max = 2000) String reason
    ) {}

    public record AdminLineItem(Long id, int position, String description, String category, Integer itineraryDay,
                                BigDecimal quantity, BigDecimal unitPriceNpr, BigDecimal lineTotalNpr,
                                BigDecimal lineTotalUsd) {}

    // Admin sees BOTH currencies (NPR = tax record, USD = what the customer pays).
    public record AdminBillResponse(
            Long id, Long bookingId, Long customerId, String invoiceNumber, String status, boolean issued,
            String documentTitle, Instant issuedAt, LocalDate issueDate, Instant lockedAt, String sellerLegalName, String sellerPanVatNumber, String sellerAddress,
            String buyerName, String buyerPan, String currencyPair, BigDecimal fxRate, boolean vatRegistered,
            BigDecimal vatPercent, BigDecimal serviceChargePercent, String payoneerLink, boolean superseded,
            Long correctsBillId, List<AdminLineItem> lineItems, BillCalculator.Totals totals,
            String grandTotalNprInWords, List<AppliedCreditInfo> appliedCredits, Long version) {}

    // A travel credit the customer flagged on this trip request, and the admin's decision.
    public record AppliedCreditInfo(Long creditId, String code, BigDecimal amountUsd, LocalDate expiresOn,
                                    boolean expired, String decision) {}

    public record CustomerLineItem(String description, String category, Integer itineraryDay, BigDecimal quantity,
                                   BigDecimal unitPriceUsd, BigDecimal lineTotalUsd) {}

    // Customer sees USD ONLY (spec currency display rule): no NPR field exists here at all,
    // so it can't leak into the app by accident.
    public record CustomerBillResponse(
            Long bookingId, String invoiceNumber, String status, LocalDate issueDate, String currency,
            List<CustomerLineItem> lineItems, BigDecimal subtotalUsd, BigDecimal serviceChargePercent,
            BigDecimal serviceChargeUsd, boolean vatApplied, BigDecimal vatPercent, BigDecimal vatUsd,
            BigDecimal grandTotalUsd, BigDecimal appliedCreditUsd, BigDecimal payableUsd, String payoneerLink) {}

    // Activity-timeline entry (spec 15.1 + 21: amendments AND corrections must show up).
    public record BillTimelineEvent(String type, Instant at, Long adminUserId, Long billId, String invoiceNumber,
                                    String fieldChanged, String oldValue, String newValue, String reason,
                                    Long linkedBillId, String linkedInvoiceNumber) {}

    public record UpdateBillingSettingsRequest(
            boolean vatRegistered,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal vatPercent,
            @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal serviceChargePercent,
            @NotBlank String sellerLegalName,
            @NotBlank String sellerPanVatNumber,
            @NotBlank String sellerAddress
    ) {}

    public record BillingSettingsResponse(boolean vatRegistered, BigDecimal vatPercent,
                                          BigDecimal serviceChargePercent, String sellerLegalName,
                                          String sellerPanVatNumber, String sellerAddress,
                                          Long updatedBy, Instant updatedAt) {
        public static BillingSettingsResponse from(BillingSettings s) {
            return new BillingSettingsResponse(s.isVatRegistered(), s.getVatPercent(), s.getServiceChargePercent(),
                    s.getSellerLegalName(), s.getSellerPanVatNumber(), s.getSellerAddress(),
                    s.getUpdatedBy(), s.getUpdatedAt());
        }
    }
}