package com.nepalgo.payment;

import com.nepalgo.payment.fx.FxRate;
import com.nepalgo.payment.settings.BillingSettings;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.Instant;

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