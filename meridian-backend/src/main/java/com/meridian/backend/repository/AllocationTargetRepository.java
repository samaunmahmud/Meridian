package com.meridian.backend.repository;

import com.meridian.backend.model.AllocationTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AllocationTargetRepository extends JpaRepository<AllocationTarget, Long> {

    List<AllocationTarget> findByPortfolioId(Long portfolioId);

    @Modifying
    @Query("delete from AllocationTarget t where t.portfolio.id = :portfolioId")
    void deleteByPortfolioId(@Param("portfolioId") Long portfolioId);
}
