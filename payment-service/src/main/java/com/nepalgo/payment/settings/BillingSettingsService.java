package com.nepalgo.payment.settings;

import com.nepalgo.payment.PaymentDtos;
import com.nepalgo.payment.audit.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BillingSettingsService {

    private static final int ROW_ID = 1;

    private final BillingSettingsRepository repository;
    private final AuditService auditService;

    public BillingSettings get() {
        return repository.findById(ROW_ID).orElseThrow(
                () -> new IllegalStateException("billing_settings row missing - V1 migration seed did not run"));
    }

    @Transactional
    public BillingSettings update(Long adminId, PaymentDtos.UpdateBillingSettingsRequest r) {
        BillingSettings s = get();
        s.setVatRegistered(r.vatRegistered());
        s.setVatPercent(r.vatPercent());
        s.setServiceChargePercent(r.serviceChargePercent());
        s.setSellerLegalName(r.sellerLegalName());
        s.setSellerPanVatNumber(r.sellerPanVatNumber());
        s.setSellerAddress(r.sellerAddress());
        s.setUpdatedBy(adminId);
        repository.save(s);

        auditService.log(adminId, "BILLING_SETTINGS_UPDATED", "BILLING_SETTINGS", (long) ROW_ID,
                "vatRegistered=" + r.vatRegistered() + ", vatPercent=" + r.vatPercent()
                        + ", serviceChargePercent=" + r.serviceChargePercent());
        return s;
    }
}