package com.meridian.backend.repository;

import com.meridian.backend.model.PriceHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** The last price recorded at or before {@code at}: what a price was at that moment. */
    Optional<PriceHistory> findFirstByTickerIdAndRecordedAtLessThanEqualOrderByRecordedAtDesc(Long tickerId, Instant at);

    /** One ticker's prices up to {@code upTo}, after (afterAt, afterId), oldest first: one page for the retention job. */
    @Query("select p.id as id, p.recordedAt as recordedAt from PriceHistory p where p.ticker.id = :group and p.recordedAt <= :upTo "
            + "and (p.recordedAt > :afterAt or (p.recordedAt = :afterAt and p.id > :afterId)) order by p.recordedAt, p.id")
    List<HistoryRow> thinningPage(@Param("group") long group, @Param("afterAt") Instant afterAt, @Param("afterId") long afterId,
                                  @Param("upTo") Instant upTo, Pageable pageable);

    /** When each ticker was last priced (only tickers that have a price appear). */
    @Query("select p.ticker.id as tickerId, max(p.recordedAt) as recordedAt from PriceHistory p group by p.ticker.id")
    List<LatestPrice> latestPerTicker();

    interface LatestPrice {
        Long getTickerId();

        Instant getRecordedAt();
    }

    /** The newest price of any ticker: how fresh the feed is as a whole. */
    Optional<PriceHistory> findFirstByOrderByRecordedAtDesc();
}
