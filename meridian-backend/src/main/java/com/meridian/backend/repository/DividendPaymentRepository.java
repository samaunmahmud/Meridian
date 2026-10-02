package com.meridian.backend.repository;

import com.meridian.backend.model.DividendPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface DividendPaymentRepository extends JpaRepository<DividendPayment, Long> {

    boolean existsByPortfolioIdAndDividendId(Long portfolioId, Long dividendId);

    @Query("select coalesce(sum(p.amount), 0) from DividendPayment p where p.portfolio.id = :portfolioId")
    BigDecimal totalForPortfolio(@Param("portfolioId") Long portfolioId);

    @Query("select coalesce(sum(p.amount), 0) from DividendPayment p where p.portfolio.id = :portfolioId and p.dividend.ticker.id = :tickerId")
    BigDecimal totalForPortfolioAndTicker(@Param("portfolioId") Long portfolioId, @Param("tickerId") Long tickerId);
}
