package com.meridian.backend.service;

import com.meridian.backend.config.MarketDataProperties;
import com.meridian.backend.exception.StalePriceException;
import com.meridian.backend.model.SupportedCurrency;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.repository.TickerRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

// One place that decides whether a stored price or exchange rate is recent enough to trade
// on. Showing the last known value (portfolio page, charts) is always fine; executing
// something at it is not, once the feed has been down for a while.
@Component
public class PriceFreshness {

    private final MarketDataProperties properties;
    private final TickerRepository tickerRepository;
    private final Clock clock;

    public PriceFreshness(MarketDataProperties properties, TickerRepository tickerRepository, Clock clock) {
        this.properties = properties;
        this.tickerRepository = tickerRepository;
        this.clock = clock;
    }

    public Duration maxPriceAge() {
        return properties.getMaxPriceAge(tickerRepository.count());
    }

    public boolean isPriceStale(Instant recordedAt) {
        return age(recordedAt).compareTo(maxPriceAge()) > 0;
    }

    public void requireFreshPrice(Ticker ticker, Instant recordedAt) {
        if (isPriceStale(recordedAt)) {
            throw new StalePriceException("price of " + ticker.getSymbol(), age(recordedAt));
        }
    }

    public void requireFreshRate(SupportedCurrency currency, Instant updatedAt) {
        if (age(updatedAt).compareTo(properties.getMaxFxRateAge()) > 0) {
            throw new StalePriceException(currency + "/USD exchange rate", age(updatedAt));
        }
    }

    private Duration age(Instant then) {
        Duration age = Duration.between(then, clock.instant());
        return age.isNegative() ? Duration.ZERO : age;
    }
}
