package com.meridian.backend;

import com.meridian.backend.dto.MoversResponse;
import com.meridian.backend.model.PriceHistory;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.service.MoversService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** The movers query against the real schema (H2): the derived "price at or before" query and the sort. */
class MoversEndpointTest extends IntegrationTestBase {

    @Autowired MoversService moversService;

    @Test
    void aTickerThatRoseSince24HoursAgoIsAGainer() {
        Ticker ticker = newTicker("1.00");
        priceHistoryRepository.deleteAll(priceHistoryRepository.findByTickerIdOrderByRecordedAtDesc(ticker.getId()));
        Instant now = Instant.now();
        priceHistoryRepository.save(new PriceHistory(ticker, new BigDecimal("40.00"), now.minus(Duration.ofHours(30))));
        priceHistoryRepository.save(new PriceHistory(ticker, new BigDecimal("42.00"), now.minus(Duration.ofHours(25))));
        priceHistoryRepository.save(new PriceHistory(ticker, new BigDecimal("462.00"), now.minusSeconds(10)));

        MoversResponse movers = moversService.movers(20); // a 1000% rise: top of the list whatever other tests left behind

        assertThat(movers.gainers()).filteredOn(m -> m.symbol().equals(ticker.getSymbol())).singleElement()
                .satisfies(m -> {
                    assertThat(m.referencePrice()).isEqualByComparingTo("42.00");
                    assertThat(m.changePercent()).isEqualByComparingTo("1000.00");
                });
    }
}
