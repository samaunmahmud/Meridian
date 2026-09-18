package com.meridian.backend.repository;

import com.meridian.backend.model.Holding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HoldingRepository extends JpaRepository<Holding, Long> {

    Optional<Holding> findByPortfolioIdAndTickerId(Long portfolioId, Long tickerId);

    List<Holding> findByPortfolioId(Long portfolioId);
}
