package com.meridian.backend.repository;

import com.meridian.backend.model.PriceHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PriceHistoryRepository extends JpaRepository<PriceHistory, Long> {

    List<PriceHistory> findByTickerIdOrderByRecordedAtDesc(Long tickerId);

    /** The newest {@code pageable.getPageSize()} prices, newest first. */
    List<PriceHistory> findByTickerIdOrderByRecordedAtDesc(Long tickerId, Pageable pageable);

    /** Every price since the given moment, newest first. */
    List<PriceHistory> findByTickerIdAndRecordedAtGreaterThanEqualOrderByRecordedAtDesc(Long tickerId, Instant since);

    Optional<PriceHistory> findFirstByTickerIdOrderByRecordedAtDesc(Long tickerId);

    /** The newest price of any ticker: how fresh the feed is as a whole. */
    Optional<PriceHistory> findFirstByOrderByRecordedAtDesc();
}
