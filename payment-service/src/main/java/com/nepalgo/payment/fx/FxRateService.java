package com.nepalgo.payment.fx;

import com.nepalgo.payment.PaymentExceptions;
import com.nepalgo.payment.audit.AuditService;
import com.nepalgo.payment.config.FeatureFlags;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FxRateService {

    private final FxRateRepository fxRateRepository;
    private final AuditService auditService;
    private final FeatureFlags flags;

    // MAKER step: records a proposal. Does NOT change the current rate.
    @Transactional
    public FxRate propose(Long adminId, String currencyPair, BigDecimal rate) {
        FxRate fx = new FxRate();
        fx.setCurrencyPair(currencyPair.trim().toUpperCase());
        fx.setRate(rate);
        fx.setProposedBy(adminId);
        fx.setStatus(FxRate.Status.PENDING);
        fxRateRepository.save(fx);
        auditService.log(adminId, "FX_RATE_PROPOSED", "FX_RATE", fx.getId(),
                fx.getCurrencyPair() + " = " + rate.toPlainString());
        return fx;
    }

    // CHECKER step: makes the proposal current and retires the old one.
    @Transactional
    public FxRate approve(Long adminId, Long fxRateId) {
        FxRate fx = fxRateRepository.findById(fxRateId)
                .orElseThrow(() -> new PaymentExceptions.FxRateNotFound(fxRateId));

        if (fx.getStatus() != FxRate.Status.PENDING) {
            throw new PaymentExceptions.InvalidFxRateState(
                    "FX rate " + fxRateId + " is " + fx.getStatus() + ", only PENDING rates can be approved");
        }

        // Segregation of duties: gated by the feature flag. Flag OFF (today)
        // => the same admin may fill both roles. Flag ON => must differ.
        if (flags.makerCheckerSegregationEnforced() && adminId.equals(fx.getProposedBy())) {
            throw new PaymentExceptions.SelfApprovalNotAllowed();
        }

        // Retire the current APPROVED row FIRST and flush it to the database
        // before promoting the new one. The partial unique index allows only
        // one APPROVED row per pair at any instant, so the order of the two
        // UPDATEs matters — without the flush, Hibernate could send them in
        // the wrong order and Postgres would (correctly) reject it.
        fxRateRepository.findByCurrencyPairAndStatus(fx.getCurrencyPair(), FxRate.Status.APPROVED)
                .ifPresent(current -> {
                    current.setStatus(FxRate.Status.RETIRED);
                    fxRateRepository.saveAndFlush(current);
                });

        fx.setStatus(FxRate.Status.APPROVED);
        fx.setApprovedBy(adminId);
        fx.setEffectiveFrom(Instant.now());
        fxRateRepository.save(fx);

        auditService.log(adminId, "FX_RATE_APPROVED", "FX_RATE", fx.getId(),
                fx.getCurrencyPair() + " = " + fx.getRate().toPlainString());
        return fx;
    }

    // What a Bill (Step 3) will copy as its frozen fx_rate.
    public FxRate current(String currencyPair) {
        String pair = currencyPair.trim().toUpperCase();
        return fxRateRepository.findByCurrencyPairAndStatus(pair, FxRate.Status.APPROVED)
                .orElseThrow(() -> new PaymentExceptions.NoApprovedFxRate(pair));
    }

    public List<FxRate> list(String currencyPair) {
        return (currencyPair == null || currencyPair.isBlank())
                ? fxRateRepository.findAllByOrderByCreatedAtDesc()
                : fxRateRepository.findByCurrencyPairOrderByCreatedAtDesc(currencyPair.trim().toUpperCase());
    }
}