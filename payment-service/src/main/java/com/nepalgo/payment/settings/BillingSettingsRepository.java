package com.nepalgo.payment.settings;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingSettingsRepository extends JpaRepository<BillingSettings, Integer> {
}