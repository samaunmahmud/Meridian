package com.meridian.backend;

import com.meridian.backend.dto.PricePointResponse;
import com.meridian.backend.exception.InvalidRequestException;
import com.meridian.backend.exception.TickerNotFoundException;
import com.meridian.backend.model.PriceHistory;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.service.MarketDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The chart endpoint: bounded, time-based, newest first. (It used to return a ticker's whole history.) */
class PriceHistoryTest extends IntegrationTestBase {

    @Autowired MarketDataService marketDataService;

    /** One price per hour for `hours` hours ending now; the price is the age in hours, so 0 is the newest. */
    private Ticker tickerWithHourlyHistory(int hours) {
        Ticker ticker = newTicker("1.00");
        priceHistoryRepository.deleteAll(priceHistoryRepository.findByTickerIdOrderByRecordedAtDesc(ticker.getId()));
        List<PriceHistory> rows = new ArrayList<>();
        Instant now = Instant.now();
        for (int h = 0; h < hours; h++) {
            rows.add(new PriceHistory(ticker, new BigDecimal(h), now.minus(Duration.ofHours(h)).minusSeconds(30)));
        }
        priceHistoryRepository.saveAll(rows);
        return ticker;
    }

    private List<PricePointResponse> history(Ticker t, String range, Integer points, Integer limit) {
        return marketDataService.getPriceHistory(t.getSymbol(), range, points, limit);
    }

    @Test
    void aRangeReturnsOnlyThePricesInsideIt() {
        Ticker ticker = tickerWithHourlyHistory(24 * 10); // ten days

        assertThat(history(ticker, "1D", null, null)).hasSize(24);
        assertThat(history(ticker, "1W", null, null)).hasSize(24 * 7);
        assertThat(history(ticker, "ALL", null, null)).hasSize(24 * 10);
        assertThat(history(ticker, null, null, null)).hasSize(24 * 10);
        assertThat(history(ticker, "1d", null, null)).as("case does not matter").hasSize(24);
    }

    @Test
    void theNewestPriceComesFirst() {
        Ticker ticker = tickerWithHourlyHistory(50);

        List<PricePointResponse> points = history(ticker, "1W", null, null);

        assertThat(points.get(0).price()).isEqualByComparingTo("0");
        assertThat(points).isSortedAccordingTo((a, b) -> b.recordedAt().compareTo(a.recordedAt()));
    }

    @Test
    void tooManyPointsAreThinnedEvenlyKeepingTheNewestAndTheOldest() {
        Ticker ticker = tickerWithHourlyHistory(1000);

        List<PricePointResponse> points = history(ticker, "ALL", 100, null);

        assertThat(points).hasSize(100);
        assertThat(points.get(0).price()).as("newest kept").isEqualByComparingTo("0");
        assertThat(points.get(99).price()).as("oldest kept").isEqualByComparingTo("999");
        // evenly spread: ages 0, ~10, ~20, ... rise steadily
        for (int i = 1; i < points.size(); i++) {
            BigDecimal step = points.get(i).price().subtract(points.get(i - 1).price());
            assertThat(step.doubleValue()).isBetween(9.0, 11.0);
        }
    }

    @Test
    void fewerPricesThanThePointLimitAreReturnedUntouched() {
        Ticker ticker = tickerWithHourlyHistory(30);

        assertThat(history(ticker, "ALL", 100, null)).hasSize(30);
    }

    @Test
    void withoutAnyParametersTheAnswerIsStillBounded() {
        Ticker ticker = tickerWithHourlyHistory(2600);

        assertThat(history(ticker, null, null, null)).hasSize(2000);
    }

    @Test
    void limitReturnsJustTheNewestPrices() {
        Ticker ticker = tickerWithHourlyHistory(50);

        List<PricePointResponse> latest = history(ticker, null, null, 1);

        assertThat(latest).hasSize(1);
        assertThat(latest.get(0).price()).isEqualByComparingTo("0");
        assertThat(history(ticker, null, null, 3)).extracting(PricePointResponse::price)
                .usingElementComparator(BigDecimal::compareTo).containsExactly(BigDecimal.ZERO, BigDecimal.ONE, new BigDecimal(2));
    }

    @Test
    void refusesNonsenseParameters() {
        Ticker ticker = tickerWithHourlyHistory(5);

        assertThatThrownBy(() -> history(ticker, "FOREVER", null, null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> history(ticker, "1D", 1, null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> history(ticker, "1D", 999999, null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> history(ticker, null, null, 0)).isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void anUnknownTickerIsNotFound() {
        assertThatThrownBy(() -> marketDataService.getPriceHistory("NOPE", null, null, null))
                .isInstanceOf(TickerNotFoundException.class);
    }
}
