package com.meridian.backend.marketdata;

import com.meridian.backend.MutableClock;
import com.meridian.backend.config.MarketDataProperties;
import com.meridian.backend.exception.MarketDataUnavailableException;
import com.meridian.backend.exception.MarketDataUnreachableException;
import com.meridian.backend.model.AssetType;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.repository.TickerRepository;
import com.meridian.backend.scheduler.PricePollingScheduler;
import com.meridian.backend.service.MarketDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PricePollingSchedulerTest {

    private MarketDataService service;
    private MutableClock clock;
    private PricePollingScheduler scheduler;

    @BeforeEach
    void setUp() {
        service = mock(MarketDataService.class);
        TickerRepository tickers = mock(TickerRepository.class);
        when(tickers.findAll()).thenReturn(List.of(
                new Ticker("AAA", "A Corp", "X", AssetType.STOCK),
                new Ticker("BBB", "B Corp", "X", AssetType.STOCK)));
        clock = new MutableClock(Instant.parse("2026-09-19T10:00:00Z"));
        // Alpha Vantage free-plan defaults: one price poll roughly every 85 minutes.
        scheduler = new PricePollingScheduler(service, tickers, new MarketDataProperties(), clock);
    }

    @Test
    void pollsOnceThenWaitsUntilTheSpacingHasPassed() {
        scheduler.pollNextTicker();
        verify(service).pollAndStore(eq("AAA"), any(), any(), any());

        clock.advance(Duration.ofSeconds(20)); // the very next scheduler tick
        scheduler.pollNextTicker();
        verifyNoMoreInteractions(service); // no request: not due yet

        clock.advance(Duration.ofMinutes(86));
        scheduler.pollNextTicker();
        verify(service).pollAndStore(eq("BBB"), any(), any(), any()); // rotates to the next ticker
    }

    @Test
    void aProviderThatIsDownDoesNotBreakTheSpacingOrTheRotation() {
        doThrow(new MarketDataUnreachableException("down", new RuntimeException("HTTP 500")))
                .when(service).pollAndStore(eq("AAA"), any(), any(), any());

        scheduler.pollNextTicker();
        verify(service).pollAndStore(eq("AAA"), any(), any(), any());

        // A failed request still counts as the poll: nothing more is sent until the spacing has
        // passed, so an outage cannot use up the daily allowance in a few minutes...
        clock.advance(Duration.ofSeconds(20));
        scheduler.pollNextTicker();
        verifyNoMoreInteractions(service);

        // ...and one ticker that keeps failing does not hold up the others.
        clock.advance(Duration.ofMinutes(86));
        scheduler.pollNextTicker();
        verify(service).pollAndStore(eq("BBB"), any(), any(), any());
    }

    @Test
    void whenTheAllowanceIsUsedUpItRetriesTheSameTickerLater() {
        doThrow(new MarketDataUnavailableException("limit")).when(service).pollAndStore(eq("AAA"), any(), any(), any());

        scheduler.pollNextTicker();
        clock.advance(Duration.ofSeconds(20));
        scheduler.pollNextTicker();

        // still on AAA both times: an unavailable provider must not skip a ticker
        verify(service, times(2)).pollAndStore(eq("AAA"), any(), any(), any());
    }
}
