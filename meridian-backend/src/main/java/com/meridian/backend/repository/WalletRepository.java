package com.meridian.backend.repository;

import com.meridian.backend.model.SupportedCurrency;
import com.meridian.backend.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    List<Wallet> findByPortfolioId(Long portfolioId);

    Optional<Wallet> findByPortfolioIdAndCurrency(Long portfolioId, SupportedCurrency currency);
}
