package com.meridian.backend;

import com.meridian.backend.dto.OrderRequest;
import com.meridian.backend.dto.PortfolioSnapshotResponse;
import com.meridian.backend.model.OrderKind;
import com.meridian.backend.model.OrderType;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.User;
import com.meridian.backend.repository.PortfolioRepository;
import com.meridian.backend.repository.PortfolioSnapshotRepository;
import com.meridian.backend.scheduler.PortfolioSnapshotScheduler;
import com.meridian.backend.service.PortfolioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The snapshot job runs on a scheduler thread, which (unlike a web request) has no database
 * session. It used to throw LazyInitializationException for every portfolio that held a stock, so
 * the equity curve was never recorded for anyone who had bought something. These tests do not run
 * inside a transaction, exactly like the scheduler thread.
 */
class PortfolioSnapshotSchedulerTest extends IntegrationTestBase {

    @Autowired PortfolioService portfolioService;
    @Autowired PortfolioRepository portfolioRepositoryBean;
    @Autowired PortfolioSnapshotRepository snapshotRepository;

    private PortfolioSnapshotScheduler scheduler() {
        return new PortfolioSnapshotScheduler(portfolioRepositoryBean, snapshotRepository, portfolioService);
    }

    @Test
    void recordsWhatAPortfolioThatHoldsAStockIsWorth() {
        User user = newUser("10000.00");
        Ticker ticker = newTicker("100.00");
        portfolioService.placeOrder(new OrderRequest(ticker.getSymbol(), OrderType.BUY, OrderKind.MARKET, new BigDecimal("3"), null, null), user);

        scheduler().recordSnapshots();

        List<PortfolioSnapshotResponse> history = portfolioService.getPortfolioHistory(user);
        assertThat(history).hasSize(1);
        // 3 shares at $100 = $300 of holdings; cash is 10000 - 300 - commission
        BigDecimal cash = portfolioOf(user).getCashBalance();
        assertThat(history.get(0).totalValue()).isEqualByComparingTo(cash.add(new BigDecimal("300.00")));
    }

    @Test
    void aPortfolioThatHoldsNothingIsRecordedToo() {
        User healthy = newUser("500.00");

        scheduler().recordSnapshots();

        assertThat(portfolioService.getPortfolioHistory(healthy)).hasSize(1);
    }
}
