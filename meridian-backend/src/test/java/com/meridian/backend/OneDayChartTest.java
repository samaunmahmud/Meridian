package com.meridian.backend;

import com.meridian.backend.dto.MoverResponse;
import com.meridian.backend.dto.PricePointResponse;
import com.meridian.backend.market.MarketCalendar;
import com.meridian.backend.model.PriceHistory;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.service.MarketDataService;
import com.meridian.backend.service.MoversService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The 1D chart and "Change today" measure the same move: both start at the previous session's close. */
@TestPropertySource(properties = "marketdata.hours-enforced=true")
class OneDayChartTest extends IntegrationTestBase {

    @Autowired MarketDataService marketDataService;
    @Autowired MoversService moversService;
    @Autowired MarketCalendar marketCalendar;

    @Test
    void theOneDayChartStartsAtThePreviousCloseSoItsChangeIsTheDaysChange() {
        Ticker ticker = newTicker("1.00");
        priceHistoryRepository.deleteAll(priceHistoryRepository.findByTickerIdOrderByRecordedAtDesc(ticker.getId()));
        Instant latest = Instant.now().minusSeconds(60);
        Instant previousClose = marketCalendar.previousStockClose(latest);
        save(ticker, "90", previousClose.minus(Duration.ofHours(2)));   // earlier in that session: not today
        save(ticker, "100", previousClose);                             // the close the day is measured from
        save(ticker, "105", latest.minusSeconds(60));
        save(ticker, "110", latest);

        List<PricePointResponse> day = marketDataService.getPriceHistory(ticker.getSymbol(), "1D", null, null);

        assertThat(day).extracting(PricePointResponse::price).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("110"), new BigDecimal("105"), new BigDecimal("100"));
        MoverResponse change = moversService.changes().stream()
                .filter(m -> m.symbol().equals(ticker.getSymbol())).findFirst().orElseThrow();
        assertThat(change.referencePrice()).isEqualByComparingTo(day.get(day.size() - 1).price());
    }

    private void save(Ticker ticker, String price, Instant at) {
        priceHistoryRepository.save(new PriceHistory(ticker, new BigDecimal(price), at));
    }
}
