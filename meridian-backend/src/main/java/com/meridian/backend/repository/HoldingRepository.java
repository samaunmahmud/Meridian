package com.meridian.backend.repository;

import com.meridian.backend.model.Holding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    Optional<Holding> findByPortfolioIdAndTickerId(Long portfolioId, Long tickerId);

    List<Holding> findByPortfolioId(Long portfolioId);

    List<Holding> findByTickerId(Long tickerId);

    /** Tickers somebody holds shares of. */
    @Query("select distinct h.ticker.id from Holding h where h.quantity > 0")
    List<Long> findHeldTickerIds();
}
