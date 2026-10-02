package com.meridian.backend;

import com.meridian.backend.client.DividendEvent;
import com.meridian.backend.client.MarketDataProvider;
import com.meridian.backend.dto.DividendInfoResponse;
import com.meridian.backend.dto.OrderRequest;
import com.meridian.backend.dto.OrderResponse;
import com.meridian.backend.exception.MarketDataUnavailableException;
import com.meridian.backend.model.AssetType;
import com.meridian.backend.model.Dividend;
import com.meridian.backend.model.Holding;
import com.meridian.backend.model.Order;
import com.meridian.backend.model.OrderKind;
import com.meridian.backend.model.OrderType;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.Transaction;
import com.meridian.backend.model.TransactionType;
import com.meridian.backend.model.User;
import com.meridian.backend.repository.DividendPaymentRepository;
import com.meridian.backend.repository.DividendRepository;
import com.meridian.backend.repository.TransactionRepository;
import com.meridian.backend.service.DividendService;
import com.meridian.backend.service.PerformanceService;
import com.meridian.backend.service.PortfolioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DividendTest extends IntegrationTestBase {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    @MockBean MarketDataProvider provider;
    @Autowired DividendService dividendService;
    @Autowired PortfolioService portfolioService;
    @Autowired PerformanceService performanceService;
    @Autowired DividendRepository dividendRepository;
    @Autowired DividendPaymentRepository paymentRepository;
    @Autowired TransactionRepository transactionRepository;

    private static LocalDate today() {
        return LocalDate.now(NEW_YORK);
    }

    private static Instant startOf(LocalDate day) {
        return day.atStartOfDay(NEW_YORK).toInstant();
    }

    /** A filled market order, moved to have happened at `at`. */
    private void trade(User user, Ticker t, OrderType type, String qty, Instant at) {
        OrderResponse placed = portfolioService.placeOrder(
                new OrderRequest(t.getSymbol(), type, OrderKind.MARKET, new BigDecimal(qty), null, null), user);
        Order order = orderRepository.findById(placed.id()).orElseThrow();
        order.setExecutedAt(at);
        orderRepository.save(order);
    }

    private Dividend dividend(Ticker t, LocalDate exDate, LocalDate payDate, String amount) {
        return dividendRepository.save(new Dividend(t, exDate, payDate, new BigDecimal(amount)));
    }

    private BigDecimal cash(User user) {
        return portfolioOf(user).getCashBalance();
    }

    @Test
    void holdersWhenTheExDateBeganArePaidOnThePaymentDateExactlyOnce() {
        User user = newUser("10000.00");
        Ticker t = newTicker("100.00");
        LocalDate exDate = today().minusDays(3);
        trade(user, t, OrderType.BUY, "10", startOf(exDate).minusSeconds(3600)); // the evening before
        Dividend d = dividend(t, exDate, today(), "0.255");
        BigDecimal before = cash(user);

        dividendService.payDue();
        dividendService.payDue();

        assertThat(cash(user)).isEqualByComparingTo(before.add(new BigDecimal("2.55")));
        assertThat(paymentRepository.existsByPortfolioIdAndDividendId(portfolioOf(user).getId(), d.getId())).isTrue();
        assertThat(dividendRepository.findById(d.getId()).orElseThrow().getPaidOutAt()).isNotNull();
        List<Transaction> dividends = transactionRepository.findByPortfolioIdOrderByCreatedAtDesc(portfolioOf(user).getId()).stream()
                .filter(x -> x.getType() == TransactionType.DIVIDEND).toList();
        assertThat(dividends).singleElement().satisfies(x -> {
            assertThat(x.getAmount()).isEqualByComparingTo("2.55");
            assertThat(x.getCurrency()).isEqualTo("USD");
            assertThat(x.getDescription()).isEqualTo("Dividend from " + t.getSymbol() + ": $0.255 × 10 shares");
        });
        assertThat(performanceService.getPerformance(user).dividendsReceived()).isEqualByComparingTo("2.55");
    }

    @Test
    void sharesBoughtFromTheExDateOnDoNotQualify() {
        User user = newUser("10000.00");
        Ticker t = newTicker("100.00");
        LocalDate exDate = today().minusDays(2);
        trade(user, t, OrderType.BUY, "4", startOf(exDate).plusSeconds(60));
        dividend(t, exDate, today(), "1.00");
        BigDecimal before = cash(user);

        dividendService.payDue();

        assertThat(cash(user)).isEqualByComparingTo(before);
    }

    @Test
    void sharesAreCountedAsTheyWereWhenTheExDateBegan() {
        User seller = newUser("10000.00");
        User adder = newUser("10000.00");
        Ticker t = newTicker("50.00");
        LocalDate exDate = today().minusDays(5);
        Instant before = startOf(exDate).minusSeconds(86_400);
        trade(seller, t, OrderType.BUY, "6", before);
        trade(seller, t, OrderType.SELL, "6", startOf(exDate).plusSeconds(3600)); // sold on the ex-date: still paid
        trade(adder, t, OrderType.BUY, "10", before);
        trade(adder, t, OrderType.SELL, "3", before.plusSeconds(60));
        trade(adder, t, OrderType.BUY, "5", startOf(exDate).plusSeconds(60));       // 7 held at the ex-date
        dividend(t, exDate, today().minusDays(1), "0.50");
        BigDecimal sellerCash = cash(seller);
        BigDecimal adderCash = cash(adder);

        dividendService.payDue();

        assertThat(cash(seller)).isEqualByComparingTo(sellerCash.add(new BigDecimal("3.00")));
        assertThat(cash(adder)).isEqualByComparingTo(adderCash.add(new BigDecimal("3.50")));
    }

    @Test
    void sharesWithNoOrderHistoryAreNotGuessedAt() {
        // Accounts from before orders were recorded can hold shares with no buy behind them:
        // when they were bought is unknown, so they are not paid rather than paid for dividends they may have missed.
        User user = newUser("10000.00");
        Ticker t = newTicker("100.00");
        holdingRepository.save(new Holding(portfolioOf(user), t, new BigDecimal("10"), new BigDecimal("90")));
        dividend(t, today().minusDays(3), today(), "1.00");
        BigDecimal before = cash(user);

        dividendService.payDue();

        assertThat(cash(user)).isEqualByComparingTo(before);
    }

    @Test
    void nothingIsPaidBeforeThePaymentDate() {
        User user = newUser("10000.00");
        Ticker t = newTicker("100.00");
        LocalDate exDate = today().minusDays(1);
        trade(user, t, OrderType.BUY, "10", startOf(exDate).minusSeconds(60));
        Dividend d = dividend(t, exDate, today().plusDays(7), "1.00");
        BigDecimal before = cash(user);

        dividendService.payDue();

        assertThat(cash(user)).isEqualByComparingTo(before);
        assertThat(dividendRepository.findById(d.getId()).orElseThrow().getPaidOutAt()).isNull();
    }

    @Test
    void aLookupStoresTheLastYearAndLaterAndUpdatesWhatIsNotPaidYet() {
        Ticker t = newTicker("100.00");
        LocalDate paidEx = today().minusMonths(3);
        Dividend paid = dividend(t, paidEx, paidEx.plusDays(10), "0.40");
        paid.setPaidOutAt(Instant.now());
        dividendRepository.save(paid);
        dividend(t, today().plusDays(10), today().plusDays(20), "0.40");
        when(provider.fetchDividends(t.getSymbol())).thenReturn(List.of(
                new DividendEvent(today().minusYears(2), today().minusYears(2), new BigDecimal("0.30")),   // too old
                new DividendEvent(paidEx, paidEx.plusDays(10), new BigDecimal("9.99")),                  // already paid: kept
                new DividendEvent(today().plusDays(10), today().plusDays(21), new BigDecimal("0.45")),   // updated
                new DividendEvent(today().minusMonths(6), today().minusMonths(7), new BigDecimal("0.40")))); // pay date before ex

        assertThat(dividendService.isRefreshDue(t)).isTrue();
        dividendService.refresh(t.getId());

        List<Dividend> stored = dividendRepository.findByTickerIdOrderByExDateDesc(t.getId());
        assertThat(stored).extracting(Dividend::getExDate).containsExactly(today().plusDays(10), paidEx, today().minusMonths(6));
        assertThat(stored.get(0).getAmount()).isEqualByComparingTo("0.45");
        assertThat(stored.get(0).getPayDate()).isEqualTo(today().plusDays(21));
        assertThat(stored.get(1).getAmount()).isEqualByComparingTo("0.40");
        assertThat(stored.get(2).getPayDate()).isEqualTo(today().minusMonths(6));
        assertThat(dividendService.isRefreshDue(tickerRepository.findById(t.getId()).orElseThrow())).isFalse();
    }

    @Test
    void theStockPageLooksDividendsUpOnceAndShowsWhatWasReceived() {
        User user = newUser("10000.00");
        Ticker t = newTicker("100.00");
        LocalDate exDate = today().minusDays(10);
        trade(user, t, OrderType.BUY, "20", startOf(exDate).minusSeconds(60));
        when(provider.fetchDividends(t.getSymbol())).thenReturn(List.of(
                new DividendEvent(exDate, today().minusDays(2), new BigDecimal("0.25")),
                new DividendEvent(today().minusMonths(3), today().minusMonths(3).plusDays(9), new BigDecimal("0.25")),
                new DividendEvent(today().plusDays(80), today().plusDays(90), new BigDecimal("0.26"))));

        DividendInfoResponse first = dividendService.getInfo(user, t.getSymbol().toLowerCase());
        dividendService.payDue();
        DividendInfoResponse second = dividendService.getInfo(user, t.getSymbol());

        verify(provider, times(1)).fetchDividends(t.getSymbol());
        assertThat(first.dividends()).hasSize(3);
        assertThat(first.dividends().get(0).amount()).isEqualByComparingTo("0.26"); // announced, newest first
        assertThat(first.trailingYearPerShare()).isEqualByComparingTo("0.50");
        assertThat(first.received()).isEqualByComparingTo("0.00");
        assertThat(second.received()).isEqualByComparingTo("5.00"); // 20 x 0.25; the older one predates the buy
    }

    @Test
    void whenTheProviderCantBeAskedTheStoredDividendsAreShown() {
        User user = newUser("1000.00");
        Ticker t = newTicker("100.00");
        dividend(t, today().minusDays(30), today().minusDays(20), "0.10");
        when(provider.fetchDividends(t.getSymbol())).thenThrow(new MarketDataUnavailableException("allowance used up"));

        assertThat(dividendService.getInfo(user, t.getSymbol()).dividends()).hasSize(1);
    }

    @Test
    void cryptoIsNeverLookedUp() {
        User user = newUser("1000.00");
        Ticker coin = tickerRepository.save(new Ticker("C" + System.nanoTime() % 1_000_000, "Coin", "CRYPTO", AssetType.CRYPTO));

        assertThat(dividendService.getInfo(user, coin.getSymbol()).dividends()).isEmpty();
        verify(provider, never()).fetchDividends(coin.getSymbol());
    }
}
