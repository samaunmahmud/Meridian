package com.meridian.backend.mysql;

import com.meridian.backend.dto.OrderRequest;
import com.meridian.backend.model.Holding;
import com.meridian.backend.model.OrderKind;
import com.meridian.backend.model.OrderType;
import com.meridian.backend.model.Portfolio;
import com.meridian.backend.model.PriceHistory;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.User;
import com.meridian.backend.repository.HoldingRepository;
import com.meridian.backend.repository.OrderRepository;
import com.meridian.backend.repository.PortfolioRepository;
import com.meridian.backend.repository.PriceHistoryRepository;
import com.meridian.backend.repository.TickerRepository;
import com.meridian.backend.repository.UserRepository;
import com.meridian.backend.service.PortfolioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.ConfigurableApplicationContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrent orders from ONE account, on a real MySQL server.
 *
 * MySQL's default isolation level (REPEATABLE READ) gives a transaction a snapshot of the database
 * at its first plain read. An order reads the ticker BEFORE it waits for the portfolio row lock, so
 * without READ COMMITTED it went on to read a holding from before the previous order committed:
 * a first-ever buy of a stock failed with a duplicate-key error (HTTP 500), and buys and sells of a
 * stock already held silently overwrote each other, leaving holdings that no longer matched the
 * orders that were paid for. H2 (used by the other tests) defaults to READ COMMITTED, so only a
 * MySQL run can show it. Found by a load test; see spring.datasource.hikari.transaction-isolation.
 */
@EnabledIfEnvironmentVariable(named = MySqlTestDatabase.URL_ENV, matches = ".+")
class MySqlConcurrencyTest {

    private ConfigurableApplicationContext app;
    private PortfolioService portfolioService;
    private UserRepository users;
    private PortfolioRepository portfolios;
    private TickerRepository tickers;
    private PriceHistoryRepository prices;
    private HoldingRepository holdings;
    private OrderRepository orders;

    @BeforeAll
    static void onlyAgainstAThrowawayDatabase() {
        MySqlTestDatabase.assertSafeToWipe();
    }

    @BeforeEach
    void startTheApp() {
        MySqlTestDatabase.wipe();
        app = MySqlTestDatabase.boot();
        portfolioService = app.getBean(PortfolioService.class);
        users = app.getBean(UserRepository.class);
        portfolios = app.getBean(PortfolioRepository.class);
        tickers = app.getBean(TickerRepository.class);
        prices = app.getBean(PriceHistoryRepository.class);
        holdings = app.getBean(HoldingRepository.class);
        orders = app.getBean(OrderRepository.class);
    }

    @AfterEach
    void stopTheApp() {
        app.close();
    }

    private User newUser() {
        User user = users.save(new User(UUID.randomUUID() + "@test.io", "not-a-real-hash"));
        portfolios.save(new Portfolio(user, new BigDecimal("1000000.00")));
        return user;
    }

    private Ticker newTicker() {
        String symbol = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase();
        Ticker ticker = tickers.save(new Ticker(symbol, "Test " + symbol, "TEST"));
        prices.save(new PriceHistory(ticker, new BigDecimal("100.00"), Instant.now()));
        return ticker;
    }

    private OrderRequest market(Ticker t, OrderType type, String qty) {
        return new OrderRequest(t.getSymbol(), type, OrderKind.MARKET, new BigDecimal(qty), null, null);
    }

    /** Runs the tasks at the same moment on separate threads and returns how many threw. */
    private int runTogether(List<Runnable> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CyclicBarrier start = new CyclicBarrier(tasks.size());
        List<Future<Boolean>> results = new ArrayList<>();
        for (Runnable task : tasks) {
            results.add(pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                try {
                    task.run();
                    return true;
                } catch (RuntimeException e) {
                    return false;
                }
            }));
        }
        int failed = 0;
        for (Future<Boolean> r : results) if (!r.get(60, TimeUnit.SECONDS)) failed++;
        pool.shutdown();
        return failed;
    }

    @Test
    void severalFirstEverBuysOfAStockAllSucceedAndAreAllCounted() throws Exception {
        for (int round = 0; round < 5; round++) {
            User user = newUser();
            Ticker ticker = newTicker();
            List<Runnable> buys = new ArrayList<>();
            for (int i = 0; i < 8; i++) buys.add(() -> portfolioService.placeOrder(market(ticker, OrderType.BUY, "1"), user));

            int failed = runTogether(buys);

            Long portfolioId = portfolios.findByUserId(user.getId()).orElseThrow().getId();
            assertThat(failed).as("round %d: orders that failed", round).isZero();
            assertThat(holdings.findByPortfolioId(portfolioId)).singleElement()
                    .extracting(Holding::getQuantity).satisfies(q -> assertThat(q).isEqualByComparingTo("8"));
            assertThat(orders.findByPortfolioIdOrderByCreatedAtDesc(portfolioId)).hasSize(8);
        }
    }

    @Test
    void buysAndSellsOfAStockAlreadyHeldNeverOverwriteEachOther() throws Exception {
        for (int round = 0; round < 5; round++) {
            User user = newUser();
            Ticker ticker = newTicker();
            portfolioService.placeOrder(market(ticker, OrderType.BUY, "10"), user); // start with 10 shares

            List<Runnable> mixed = new ArrayList<>();
            for (int i = 0; i < 9; i++) mixed.add(() -> portfolioService.placeOrder(market(ticker, OrderType.BUY, "1"), user));
            for (int i = 0; i < 5; i++) mixed.add(() -> portfolioService.placeOrder(market(ticker, OrderType.SELL, "1"), user));

            int failed = runTogether(mixed);

            Long portfolioId = portfolios.findByUserId(user.getId()).orElseThrow().getId();
            assertThat(failed).as("round %d: orders that failed", round).isZero();
            // 10 + 9 - 5: every paid-for order must be reflected in the holding
            assertThat(holdings.findByPortfolioId(portfolioId)).singleElement()
                    .extracting(Holding::getQuantity).satisfies(q -> assertThat(q).isEqualByComparingTo("14"));
        }
    }
}
