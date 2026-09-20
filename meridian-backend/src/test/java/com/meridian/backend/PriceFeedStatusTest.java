package com.meridian.backend;

import com.meridian.backend.market.MarketCalendar;
import com.meridian.backend.model.AssetType;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.repository.PriceHistoryRepository;
import com.meridian.backend.repository.PriceHistoryRepository.LatestPrice;
import com.meridian.backend.repository.TickerRepository;
import com.meridian.backend.service.PriceFeedStatus;
import com.meridian.backend.service.PriceFreshness;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** "stale" means prices should be arriving and are not: a closed stock market with its close recorded is fine. */
class PriceFeedStatusTest {

    private static final Instant SATURDAY = Instant.parse("2026-09-19T15:00:00Z");
    private static final Instant FRIDAY_CLOSE = Instant.parse("2026-09-18T20:00:00Z");
    private static final Instant MONDAY_10AM = Instant.parse("2026-09-21T14:00:00Z");

    private final MutableClock clock = new MutableClock(SATURDAY);
    private final List<Ticker> tickers = new ArrayList<>();
    private final List<LatestPrice> latest = new ArrayList<>();

    private static Ticker ticker(long id, AssetType type) {
        Ticker t = new Ticker("T" + id, "T" + id, "X", type);
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    private void priced(Ticker t, Instant at) {
        tickers.add(t);
        latest.add(new LatestPrice() {
            public Long getTickerId() { return t.getId(); }
            public Instant getRecordedAt() { return at; }
        });
    }

    private PriceFeedStatus.Status check(boolean hoursEnforced) {
        TickerRepository tickerRepo = mock(TickerRepository.class);
        when(tickerRepo.findAll()).thenReturn(tickers);
        PriceHistoryRepository priceRepo = mock(PriceHistoryRepository.class);
        when(priceRepo.latestPerTicker()).thenReturn(latest);
        PriceFreshness freshness = mock(PriceFreshness.class);
        when(freshness.maxPriceAge()).thenReturn(Duration.ofMinutes(15));
        return new PriceFeedStatus(tickerRepo, priceRepo, freshness, new MarketCalendar(clock, hoursEnforced), clock).check();
    }

    @Test
    void noPricesAtAllIsEmpty() {
        assertThat(check(true).feed()).isEqualTo("empty");
    }

    @Test
    void anOpenMarketWithARecentPriceIsOk() {
        clock.set(MONDAY_10AM);
        priced(ticker(1, AssetType.STOCK), MONDAY_10AM.minusSeconds(60));

        assertThat(check(true).feed()).isEqualTo("ok");
    }

    @Test
    void anOpenMarketWithNoRecentPriceIsStale() {
        clock.set(MONDAY_10AM);
        priced(ticker(1, AssetType.STOCK), MONDAY_10AM.minus(Duration.ofHours(2)));

        assertThat(check(true).feed()).isEqualTo("stale");
    }

    @Test
    void aQuietWeekendIsNotStaleWhenTheClosingPriceIsRecorded() {
        priced(ticker(1, AssetType.STOCK), FRIDAY_CLOSE.plusSeconds(120)); // a day and a bit old, but it IS the close

        PriceFeedStatus.Status status = check(true);

        assertThat(status.feed()).isEqualTo("ok");
        assertThat(status.newestPriceAgeSeconds()).isGreaterThan(3600);
    }

    @Test
    void aWeekendIsStaleIfTheClosingPriceWasNeverCaptured() {
        priced(ticker(1, AssetType.STOCK), FRIDAY_CLOSE.minus(Duration.ofHours(5)));

        assertThat(check(true).feed()).isEqualTo("stale");
    }

    @Test
    void cryptoIsExpectedAroundTheClockSoOldCryptoPricesAreStaleEvenOnAWeekend() {
        priced(ticker(1, AssetType.STOCK), FRIDAY_CLOSE.plusSeconds(120));
        priced(ticker(2, AssetType.CRYPTO), SATURDAY.minus(Duration.ofHours(3)));

        assertThat(check(true).feed()).isEqualTo("stale");
    }

    @Test
    void freshCryptoKeepsAWeekendOk() {
        priced(ticker(1, AssetType.STOCK), FRIDAY_CLOSE.plusSeconds(120));
        priced(ticker(2, AssetType.CRYPTO), SATURDAY.minusSeconds(30));

        assertThat(check(true).feed()).isEqualTo("ok");
    }

    @Test
    void withTradingHoursOffAnOldStockPriceIsStale() {
        priced(ticker(1, AssetType.STOCK), FRIDAY_CLOSE.plusSeconds(120));

        assertThat(check(false).feed()).isEqualTo("stale");
    }
}
