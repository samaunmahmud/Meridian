package com.meridian.backend.repository;

import com.meridian.backend.model.RecurringOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RecurringOrderRepository extends JpaRepository<RecurringOrder, Long> {
    List<RecurringOrder> findByPortfolioIdOrderByCreatedAtDesc(Long portfolioId);

    List<RecurringOrder> findByActiveTrueAndNextRunAtLessThanEqual(Instant now);

    Optional<RecurringOrder> findByIdAndPortfolioId(Long id, Long portfolioId);
}
