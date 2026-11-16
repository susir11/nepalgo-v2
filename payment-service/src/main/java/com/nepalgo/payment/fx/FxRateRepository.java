package com.nepalgo.payment.fx;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FxRateRepository extends JpaRepository<FxRate, Long> {
    Optional<FxRate> findByCurrencyPairAndStatus(String currencyPair, FxRate.Status status);
    List<FxRate> findByCurrencyPairOrderByCreatedAtDesc(String currencyPair);
    List<FxRate> findAllByOrderByCreatedAtDesc();
}