package com.meridian.backend.service;

import com.meridian.backend.market.MarketCalendar;
import com.meridian.backend.model.AssetType;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.repository.PriceHistoryRepository;
import com.meridian.backend.repository.PriceHistoryRepository.LatestPrice;
import com.meridian.backend.repository.TickerRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Is the price feed working? "stale" means prices SHOULD be arriving and are not. A stock whose market is
 * closed and whose closing price is recorded is not overdue (nothing moves until the open, and the poller
 * deliberately makes no requests), so a quiet weekend is "ok". The feed is stale when at least one ticker
 * should be getting fresh prices and none of those has one recent enough.
 */
@Component
public class PriceFeedStatus {

    public record Status(String feed, Long newestPriceAgeSeconds) {
    }

    private final TickerRepository tickerRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final PriceFreshness priceFreshness;
    private final MarketCalendar marketCalendar;
    private final Clock clock;

    public PriceFeedStatus(TickerRepository tickerRepository, PriceHistoryRepository priceHistoryRepository,
                           PriceFreshness priceFreshness, MarketCalendar marketCalendar, Clock clock) {
        this.tickerRepository = tickerRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.priceFreshness = priceFreshness;
        this.marketCalendar = marketCalendar;
        this.clock = clock;
    }

    public Status check() {
        Map<Long, Instant> latest = priceHistoryRepository.latestPerTicker().stream()
                .collect(Collectors.toMap(LatestPrice::getTickerId, LatestPrice::getRecordedAt));
        if (latest.isEmpty()) {
            return new Status("empty", null);
        }
        Instant now = clock.instant();
        Instant newest = latest.values().stream().max(Instant::compareTo).orElseThrow();
        long newestAge = Math.max(0, Duration.between(newest, now).toSeconds());

        Duration maxAge = priceFreshness.maxPriceAge();
        boolean stocksOpen = marketCalendar.status(AssetType.STOCK, now).open();
        Instant lastClose = marketCalendar.lastStockClose(now);

        List<Ticker> shouldBeFresh = tickerRepository.findAll().stream()
                .filter(t -> t.getAssetType() == AssetType.CRYPTO || stocksOpen || lastClose == null
                        || !latest.containsKey(t.getId()) || latest.get(t.getId()).isBefore(lastClose))
                .toList();

        boolean anyFresh = shouldBeFresh.stream().anyMatch(t -> {
            Instant at = latest.get(t.getId());
            return at != null && Duration.between(at, now).compareTo(maxAge) <= 0;
        });
        boolean stale = !shouldBeFresh.isEmpty() && !anyFresh;
        return new Status(stale ? "stale" : "ok", newestAge);
    }
}
