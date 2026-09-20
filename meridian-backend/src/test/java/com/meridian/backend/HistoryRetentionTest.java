package com.meridian.backend;

import com.meridian.backend.dto.PortfolioSnapshotResponse;
import com.meridian.backend.exception.InvalidRequestException;
import com.meridian.backend.model.Portfolio;
import com.meridian.backend.model.PortfolioSnapshot;
import com.meridian.backend.model.PriceHistory;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.User;
import com.meridian.backend.repository.PortfolioSnapshotRepository;
import com.meridian.backend.retention.HistoryRetention;
import com.meridian.backend.service.PortfolioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The nightly clean-up against the real tables. Old history is thinned; the last 7 days and other data are not. */
class HistoryRetentionTest extends IntegrationTestBase {

    @Autowired HistoryRetention retention;
    @Autowired PortfolioService portfolioService;
    @Autowired PortfolioSnapshotRepository snapshotRepository;

    private void halfHourPrices(Ticker ticker, int daysAgoStart, int daysAgoEnd) {
        Instant now = Instant.now();
        List<PriceHistory> rows = new ArrayList<>();
        for (Instant at = now.minus(Duration.ofDays(daysAgoStart)); at.isBefore(now.minus(Duration.ofDays(daysAgoEnd)));
             at = at.plus(Duration.ofMinutes(30))) {
            rows.add(new PriceHistory(ticker, new BigDecimal("100.00"), at));
        }
        priceHistoryRepository.saveAll(rows);
    }

    private long count(Ticker t, Instant from, Instant to) {
        return priceHistoryRepository.findByTickerIdOrderByRecordedAtDesc(t.getId()).stream()
                .filter(p -> !p.getRecordedAt().isBefore(from) && p.getRecordedAt().isBefore(to)).count();
    }

    @Test
    void oldPricesAreThinnedAndTheLastWeekAndOtherTickersAreUntouched() {
        Ticker busy = newTicker("100.00");
        Ticker other = newTicker("50.00");
        priceHistoryRepository.deleteAll(priceHistoryRepository.findByTickerIdOrderByRecordedAtDesc(busy.getId()));
        halfHourPrices(busy, 20, 0);   // 20 days of a price every 30 minutes
        Instant now = Instant.now();
        long recentBefore = count(busy, now.minus(Duration.ofDays(7)), now.plusSeconds(1));
        long otherBefore = priceHistoryRepository.findByTickerIdOrderByRecordedAtDesc(other.getId()).size();
        PriceHistory newest = priceHistoryRepository.findFirstByTickerIdOrderByRecordedAtDesc(busy.getId()).orElseThrow();

        HistoryRetention.Result result = retention.run();

        assertThat(result.priceRows()).isGreaterThan(250);
        assertThat(count(busy, now.minus(Duration.ofDays(7)), now.plusSeconds(1))).as("the last 7 days keep every row").isEqualTo(recentBefore);
        // 13 old days at one row per hour (a partial hour at each end can add one)
        long old = count(busy, now.minus(Duration.ofDays(21)), now.minus(Duration.ofDays(7)));
        assertThat(old).isBetween(13L * 24 - 2, 13L * 24 + 2);
        assertThat(priceHistoryRepository.findFirstByTickerIdOrderByRecordedAtDesc(busy.getId()).orElseThrow().getId())
                .as("the newest price is never removed").isEqualTo(newest.getId());
        assertThat(priceHistoryRepository.findByTickerIdOrderByRecordedAtDesc(other.getId())).as("another ticker").hasSize((int) otherBefore);
    }

    @Test
    void runningTheCleanUpTwiceRemovesNothingTheSecondTime() {
        Ticker ticker = newTicker("100.00");
        halfHourPrices(ticker, 12, 8);
        retention.run();
        long afterFirst = priceHistoryRepository.count();

        HistoryRetention.Result second = retention.run();

        assertThat(second.priceRows()).isZero();
        assertThat(priceHistoryRepository.count()).isEqualTo(afterFirst);
    }

    @Test
    void oldPortfolioSnapshotsAreThinnedToo() {
        User user = newUser("1000.00");
        Portfolio portfolio = portfolioOf(user);
        Instant now = Instant.now();
        List<PortfolioSnapshot> rows = new ArrayList<>();
        for (Instant at = now.minus(Duration.ofDays(15)); at.isBefore(now); at = at.plus(Duration.ofMinutes(30))) {
            rows.add(new PortfolioSnapshot(portfolio, new BigDecimal("1000.00"), at));
        }
        snapshotRepository.saveAll(rows);
        long recentBefore = snapshotRepository.findByPortfolioIdOrderByRecordedAtAsc(portfolio.getId()).stream()
                .filter(s -> s.getRecordedAt().isAfter(now.minus(Duration.ofDays(7)))).count();

        HistoryRetention.Result result = retention.run();

        assertThat(result.snapshotRows()).isBetween(170, 200); // 8 old days: 384 rows become 192
        List<PortfolioSnapshot> left = snapshotRepository.findByPortfolioIdOrderByRecordedAtAsc(portfolio.getId());
        assertThat(left.stream().filter(s -> s.getRecordedAt().isAfter(now.minus(Duration.ofDays(7)))).count()).isEqualTo(recentBefore);
        assertThat(left.stream().filter(s -> !s.getRecordedAt().isAfter(now.minus(Duration.ofDays(7)))).count())
                .isBetween(8L * 24 - 2, 8L * 24 + 2);
    }

    @Test
    void theEquityHistoryEndpointIsBoundedAndKeepsBothEnds() {
        User user = newUser("1000.00");
        Portfolio portfolio = portfolioOf(user);
        Instant start = Instant.now().minus(Duration.ofDays(3));
        List<PortfolioSnapshot> rows = new ArrayList<>();
        for (int i = 0; i < 700; i++) {
            rows.add(new PortfolioSnapshot(portfolio, new BigDecimal(i), start.plus(Duration.ofMinutes(5L * i))));
        }
        snapshotRepository.saveAll(rows);

        List<PortfolioSnapshotResponse> defaultHistory = portfolioService.getPortfolioHistory(user);
        List<PortfolioSnapshotResponse> small = portfolioService.getPortfolioHistory(user, 100);

        assertThat(defaultHistory).hasSize(500);
        assertThat(small).hasSize(100);
        assertThat(small.get(0).totalValue()).as("oldest first, oldest kept").isEqualByComparingTo("0");
        assertThat(small.get(99).totalValue()).as("newest kept").isEqualByComparingTo("699");
        assertThat(small).isSortedAccordingTo((a, b) -> a.recordedAt().compareTo(b.recordedAt()));
        assertThatThrownBy(() -> portfolioService.getPortfolioHistory(user, 1)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> portfolioService.getPortfolioHistory(user, 100000)).isInstanceOf(InvalidRequestException.class);
    }
}
