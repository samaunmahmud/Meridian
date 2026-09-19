package com.meridian.backend;

import com.meridian.backend.dto.OrderRequest;
import com.meridian.backend.model.Holding;
import com.meridian.backend.model.OrderKind;
import com.meridian.backend.model.OrderType;
import com.meridian.backend.model.Portfolio;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.User;
import com.meridian.backend.service.PortfolioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fires many orders at the same instant. Without a lock on the user's
 * portfolio, two requests read the same balance, both pass the "enough
 * money?" check, and both spend it (a lost update) — money is created or
 * destroyed. These tests pin down the invariant: what was spent always
 * matches what was actually allowed.
 */
class ConcurrencyTest extends IntegrationTestBase {

    @Autowired PortfolioService portfolioService;

    @Test
    void concurrentMarketBuysNeverSpendMoreThanTheBalance() throws Exception {
        User user = newUser("1000.00");
        Ticker ticker = newTicker("100.00"); // 1 share = $100 + $1.00 minimum fee = $101

        int succeeded = runConcurrently(20, () -> portfolioService.placeOrder(
                new OrderRequest(ticker.getSymbol(), OrderType.BUY, OrderKind.MARKET, BigDecimal.ONE, null, null), user));

        // 1000 / 101 = 9.9, so exactly 9 buys can be afforded — no more, no fewer.
        assertThat(succeeded).isEqualTo(9);
        Portfolio portfolio = portfolioOf(user);
        assertThat(portfolio.getCashBalance()).isEqualByComparingTo("91.00");
        Holding holding = holdingRepository.findByPortfolioIdAndTickerId(portfolio.getId(), ticker.getId()).orElseThrow();
        assertThat(holding.getQuantity()).isEqualByComparingTo("9");
    }

    @Test
    void concurrentMarketSellsNeverSellMoreThanTheHolding() throws Exception {
        User user = newUser("0.00");
        Ticker ticker = newTicker("100.00");
        Portfolio portfolio = portfolioOf(user);
        holdingRepository.save(new Holding(portfolio, ticker, new BigDecimal("5"), new BigDecimal("90.00")));

        int succeeded = runConcurrently(20, () -> portfolioService.placeOrder(
                new OrderRequest(ticker.getSymbol(), OrderType.SELL, OrderKind.MARKET, BigDecimal.ONE, null, null), user));

        // Only 5 shares exist to sell; each sale nets $100 - $1.00 fee = $99.
        assertThat(succeeded).isEqualTo(5);
        assertThat(portfolioOf(user).getCashBalance()).isEqualByComparingTo("495.00");
        assertThat(holdingRepository.findByPortfolioIdAndTickerId(portfolio.getId(), ticker.getId())).isEmpty();
    }
}
