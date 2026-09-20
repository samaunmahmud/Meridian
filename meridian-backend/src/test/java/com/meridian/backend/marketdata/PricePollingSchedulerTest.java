package com.meridian.backend.marketdata;

import com.meridian.backend.MutableClock;
import com.meridian.backend.config.MarketDataProperties;
import com.meridian.backend.exception.MarketDataUnavailableException;
import com.meridian.backend.exception.MarketDataUnreachableException;
import com.meridian.backend.market.MarketCalendar;
import com.meridian.backend.model.PriceHistory;
import com.meridian.backend.repository.PriceHistoryRepository;
import com.meridian.backend.model.AssetType;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.repository.TickerRepository;
import com.meridian.backend.scheduler.PricePollingScheduler;
import com.meridian.backend.service.MarketDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PricePollingSchedulerTest {

    private MarketDataService service;
    private MutableClock clock;
    private PricePollingScheduler scheduler;
    private PriceHistoryRepository prices;

    @BeforeEach
    void setUp() {
        service = mock(MarketDataService.class);
        TickerRepository tickers = mock(TickerRepository.class);
        when(tickers.findAll()).thenReturn(List.of(
                new Ticker("AAA", "A Corp", "X", AssetType.STOCK),
                new Ticker("BBB", "B Corp", "X", AssetType.STOCK)));
        clock = new MutableClock(Instant.parse("2026-09-19T10:00:00Z"));
        // Alpha Vantage free-plan defaults: one price poll roughly every 85 minutes.
        prices = mock(PriceHistoryRepository.class);
        // trading hours off: every ticker is always due (the rotation tests below are about the rotation)
        scheduler = new PricePollingScheduler(service, tickers, new MarketDataProperties(), clock,
                new MarketCalendar(clock, false), prices);
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

    // ---- trading hours: no requests for a closed stock market

    private static final Instant SATURDAY = Instant.parse("2026-09-19T15:00:00Z");          // closed
    private static final Instant MONDAY_10AM = Instant.parse("2026-09-21T14:00:00Z");      // open
    private static final Instant FRIDAY_CLOSE = Instant.parse("2026-09-18T20:00:00Z");     // 16:00 New York

    private PricePollingScheduler withHours(List<Ticker> tickers) {
        TickerRepository repo = mock(TickerRepository.class);
        when(repo.findAll()).thenReturn(tickers);
        return new PricePollingScheduler(service, repo, new MarketDataProperties(), clock,
                new MarketCalendar(clock, true), prices);
    }

    private static Ticker stock(long id, String symbol) {
        Ticker t = new Ticker(symbol, symbol + " Corp", "X", AssetType.STOCK);
        org.springframework.test.util.ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    private static Ticker crypto(long id, String symbol) {
        Ticker t = new Ticker(symbol, symbol, "CRYPTO", AssetType.CRYPTO);
        org.springframework.test.util.ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    private void latestPriceAt(long tickerId, Instant at) {
        PriceHistory p = new PriceHistory(null, BigDecimal.TEN, at);
        when(prices.findFirstByTickerIdOrderByRecordedAtDesc(tickerId)).thenReturn(Optional.of(p));
    }

    @Test
    void aClosedStockWhoseClosingPriceIsRecordedIsNotPolledAgain() {
        clock.set(SATURDAY);
        latestPriceAt(1, FRIDAY_CLOSE.plusSeconds(300)); // captured just after Friday's close
        PricePollingScheduler s = withHours(List.of(stock(1, "AAA")));

        s.pollNextTicker();

        verifyNoMoreInteractions(service);
    }

    @Test
    void aClosedStockIsPolledOnceToCaptureTheClosingPriceIfItHasNoneYet() {
        clock.set(SATURDAY);
        latestPriceAt(1, FRIDAY_CLOSE.minusSeconds(3600)); // last price is from before the close
        PricePollingScheduler s = withHours(List.of(stock(1, "AAA")));

        s.pollNextTicker();

        verify(service).pollAndStore(eq("AAA"), any(), any(), any());
    }

    @Test
    void aStockWithNoPriceAtAllIsPolledEvenWhenClosed() {
        clock.set(SATURDAY);
        when(prices.findFirstByTickerIdOrderByRecordedAtDesc(anyLong())).thenReturn(Optional.empty());
        PricePollingScheduler s = withHours(List.of(stock(1, "AAA")));

        s.pollNextTicker();

        verify(service).pollAndStore(eq("AAA"), any(), any(), any());
    }

    @Test
    void cryptoKeepsBeingPolledWhileStocksAreSkipped() {
        clock.set(SATURDAY);
        latestPriceAt(1, FRIDAY_CLOSE.plusSeconds(300));
        PricePollingScheduler s = withHours(List.of(stock(1, "AAA"), crypto(2, "BTC")));

        s.pollNextTicker();

        verify(service).pollAndStore(eq("BTC"), any(), any(), any());
        verify(service, never()).pollAndStore(eq("AAA"), any(), any(), any());
    }

    @Test
    void whenNothingNeedsAPollTheSpacingIsNotUsedUpSoTheOpenIsNotDelayed() {
        clock.set(SATURDAY);
        latestPriceAt(1, FRIDAY_CLOSE.plusSeconds(300));
        PricePollingScheduler s = withHours(List.of(stock(1, "AAA")));
        s.pollNextTicker(); // nothing to do over the weekend
        verifyNoMoreInteractions(service);

        clock.set(MONDAY_10AM); // the market opens: the very next tick polls
        s.pollNextTicker();

        verify(service).pollAndStore(eq("AAA"), any(), any(), any());
    }

    @Test
    void anOpenMarketPollsEveryStockAsBefore() {
        clock.set(MONDAY_10AM);
        latestPriceAt(1, MONDAY_10AM.minusSeconds(10));
        PricePollingScheduler s = withHours(List.of(stock(1, "AAA")));

        s.pollNextTicker();

        verify(service).pollAndStore(eq("AAA"), any(), any(), any());
    }
}
