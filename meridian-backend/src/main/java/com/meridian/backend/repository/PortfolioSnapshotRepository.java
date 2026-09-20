package com.meridian.backend.repository;

import com.meridian.backend.model.PortfolioSnapshot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface PortfolioSnapshotRepository extends JpaRepository<PortfolioSnapshot, Long> {
    List<PortfolioSnapshot> findByPortfolioIdOrderByRecordedAtAsc(Long portfolioId);

    /** One portfolio's snapshots up to {@code upTo}, after (afterAt, afterId), oldest first: one page for the retention job. */
    @Query("select s.id as id, s.recordedAt as recordedAt from PortfolioSnapshot s where s.portfolio.id = :group and s.recordedAt <= :upTo "
            + "and (s.recordedAt > :afterAt or (s.recordedAt = :afterAt and s.id > :afterId)) order by s.recordedAt, s.id")
    List<HistoryRow> thinningPage(@Param("group") long group, @Param("afterAt") Instant afterAt, @Param("afterId") long afterId,
                                  @Param("upTo") Instant upTo, Pageable pageable);
}
