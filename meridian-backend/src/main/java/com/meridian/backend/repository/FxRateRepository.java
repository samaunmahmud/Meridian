package com.meridian.backend.repository;

import com.meridian.backend.model.FxRate;
import com.meridian.backend.model.SupportedCurrency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FxRateRepository extends JpaRepository<FxRate, Long> {
    Optional<FxRate> findByBaseCurrencyAndQuoteCurrency(SupportedCurrency baseCurrency, SupportedCurrency quoteCurrency);
}
