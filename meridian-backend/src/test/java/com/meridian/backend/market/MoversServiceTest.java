package com.meridian.backend.market;

import com.meridian.backend.dto.MoverResponse;
import com.meridian.backend.dto.MoversResponse;
import com.meridian.backend.model.AssetType;
import com.meridian.backend.model.PriceHistory;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.repository.PriceHistoryRepository;
import com.meridian.backend.repository.TickerRepository;
import com.meridian.backend.service.MoversService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MoversServiceTest {

    private static final Instant THU_CLOSE = Instant.parse("2026-09-17T20:00:00Z");
    private static final Instant FRI_CLOSE = Instant.parse("2026-09-18T20:00:00Z");
    private static final Instant MON_11AM = Instant.parse("2026-09-21T15:00:00Z");

    private final TickerRepository tickers = mock(TickerRepository.class);
    private final PriceHistoryRepository prices = mock(PriceHistoryRepository.class);
    private final List<Ticker> tracked = new ArrayList<>();
    private final List<PriceHistory> history = new ArrayList<>();

    private MoversService service(boolean hoursEnforced) {
        when(tickers.findAll()).thenReturn(tracked);
        // A tiny in-memory stand-in for the two queries the service makes.
        when(prices.findFirstByTickerIdOrderByRecordedAtDesc(anyLong())).thenAnswer(inv -> latest(inv.getArgument(0), Instant.MAX));
        when(prices.findFirstByTickerIdAndRecordedAtLessThanEqualOrderByRecordedAtDesc(anyLong(), any()))
                .thenAnswer(inv -> latest(inv.getArgument(0), inv.getArgument(1)));
        return new MoversService(tickers, prices, new MarketCalendar(Clock.systemUTC(), hoursEnforced));
    }

    private Optional<PriceHistory> latest(long tickerId, Instant atOrBefore) {
        return history.stream()
                .filter(p -> p.getTicker().getId() == tickerId && !p.getRecordedAt().isAfter(atOrBefore))
                .max(Comparator.comparing(PriceHistory::getRecordedAt));
    }

    private Ticker ticker(String symbol, AssetType type) {
        Ticker t = new Ticker(symbol, symbol + " Inc", "X", type);
        ReflectionTestUtils.setField(t, "id", (long) tracked.size() + 1);
        tracked.add(t);
        return t;
    }

    private void price(Ticker t, String price, Instant at) {
        history.add(new PriceHistory(t, new BigDecimal(price), at));
    }

    @Test
    void aStockIsMeasuredFromThePreviousSessionsCloseNotFromItsLastPoll() {
        Ticker aaa = ticker("AAA", AssetType.STOCK);
        price(aaa, "90", THU_CLOSE.minusSeconds(3600));
        price(aaa, "100", FRI_CLOSE.minusSeconds(60));    // Friday's close: the reference on Monday
        price(aaa, "108", MON_11AM.minusSeconds(3600));
        price(aaa, "110", MON_11AM);

        MoverResponse move = service(true).movers(5).gainers().get(0);

        assertThat(move.referencePrice()).isEqualByComparingTo("100");
        assertThat(move.change()).isEqualByComparingTo("10");
        assertThat(move.changePercent()).isEqualByComparingTo("10.00");
    }

    @Test
    void overTheWeekendAStockShowsFridaysMove() {
        Ticker aaa = ticker("AAA", AssetType.STOCK);
        price(aaa, "100", THU_CLOSE.minusSeconds(60));
        price(aaa, "95", FRI_CLOSE.plusSeconds(120)); // the close, recorded just after 16:00

        MoversResponse movers = service(true).movers(5);

        assertThat(movers.losers()).singleElement()
                .satisfies(m -> assertThat(m.changePercent()).isEqualByComparingTo("-5.00"));
    }

    @Test
    void cryptoIsMeasuredOver24Hours() {
        Ticker btc = ticker("BTC", AssetType.CRYPTO);
        price(btc, "50000", MON_11AM.minusSeconds(25 * 3600));
        price(btc, "40000", MON_11AM.minusSeconds(23 * 3600)); // inside the window: not the reference
        price(btc, "51000", MON_11AM);

        MoverResponse move = service(true).movers(5).gainers().get(0);

        assertThat(move.referencePrice()).isEqualByComparingTo("50000");
        assertThat(move.changePercent()).isEqualByComparingTo("2.00");
    }

    @Test
    void withoutTradingHoursStocksAreMeasuredOver24HoursToo() {
        Ticker aaa = ticker("AAA", AssetType.STOCK);
        price(aaa, "200", MON_11AM.minusSeconds(24 * 3600));
        price(aaa, "150", MON_11AM);

        assertThat(service(false).movers(5).losers().get(0).changePercent()).isEqualByComparingTo("-25.00");
    }

    @Test
    void gainersAndLosersAreSortedBiggestFirstAndLimited() {
        String[][] moves = {{"A", "101"}, {"B", "130"}, {"C", "110"}, {"D", "70"}, {"E", "95"}, {"F", "100"}};
        for (String[] m : moves) {
            Ticker t = ticker(m[0], AssetType.CRYPTO);
            price(t, "100", MON_11AM.minusSeconds(25 * 3600));
            price(t, m[1], MON_11AM);
        }

        MoversResponse movers = service(true).movers(2);

        assertThat(movers.gainers()).extracting(MoverResponse::symbol).containsExactly("B", "C");
        assertThat(movers.losers()).extracting(MoverResponse::symbol).containsExactly("D", "E");
        // F did not move: in neither list
    }

    @Test
    void aTickerWithNoPriceOldEnoughYetIsLeftOut() {
        Ticker fresh = ticker("NEW", AssetType.CRYPTO);
        price(fresh, "10", MON_11AM.minusSeconds(3600));
        price(fresh, "20", MON_11AM);
        ticker("NONE", AssetType.STOCK); // no price at all

        MoversResponse movers = service(true).movers(5);

        assertThat(movers.gainers()).isEmpty();
        assertThat(movers.losers()).isEmpty();
    }
}
