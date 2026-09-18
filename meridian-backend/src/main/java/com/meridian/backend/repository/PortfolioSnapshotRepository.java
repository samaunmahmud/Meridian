package com.meridian.backend.repository;

import com.meridian.backend.model.PortfolioSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, Long> {
    List<PortfolioSnapshot> findByPortfolioIdOrderByRecordedAtAsc(Long portfolioId);
}
