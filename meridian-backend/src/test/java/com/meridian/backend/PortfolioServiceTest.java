package com.meridian.backend;

import com.meridian.backend.dto.OrderRequest;
import com.meridian.backend.dto.OrderResponse;
import com.meridian.backend.dto.TransactionResponse;
import com.meridian.backend.exception.InsufficientFundsException;
import com.meridian.backend.exception.InsufficientSharesException;
import com.meridian.backend.model.Holding;
import com.meridian.backend.model.OrderKind;
import com.meridian.backend.model.OrderType;
import com.meridian.backend.model.Portfolio;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.TransactionType;
import com.meridian.backend.model.User;
import com.meridian.backend.service.PortfolioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioServiceTest extends IntegrationTestBase {

    @Autowired PortfolioService portfolioService;

    private OrderRequest market(Ticker t, OrderType type, String qty) {
        return new OrderRequest(t.getSymbol(), type, OrderKind.MARKET, new BigDecimal(qty), null, null);
    }

    @Test
    void marketBuyDebitsCostAndCommissionAndCreatesHolding() {
        User user = newUser("1000.00");
        Ticker ticker = newTicker("100.00");

        OrderResponse order = portfolioService.placeOrder(market(ticker, OrderType.BUY, "2"), user);

        assertThat(order.feeAmount()).isEqualByComparingTo("1.00"); // 0.25% of 200 = 0.50, minimum is 1.00
        Portfolio portfolio = portfolioOf(user);
        assertThat(portfolio.getCashBalance()).isEqualByComparingTo("799.00");
        Holding holding = holdingRepository.findByPortfolioIdAndTickerId(portfolio.getId(), ticker.getId()).orElseThrow();
        assertThat(holding.getQuantity()).isEqualByComparingTo("2");
        assertThat(holding.getAvgCost()).isEqualByComparingTo("100.00");

        List<TransactionResponse> ledger = portfolioService.getTransactionHistory(user);
        assertThat(ledger).extracting(TransactionResponse::type)
                .containsExactlyInAnyOrder(TransactionType.BUY, TransactionType.FEE);
    }

    @Test
    void buyWithInsufficientFundsChangesNothing() {
        User user = newUser("50.00");
        Ticker ticker = newTicker("100.00");

        assertThatThrownBy(() -> portfolioService.placeOrder(market(ticker, OrderType.BUY, "1"), user))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(portfolioOf(user).getCashBalance()).isEqualByComparingTo("50.00");
        assertThat(portfolioService.getOrderHistory(user)).isEmpty();
        assertThat(holdingRepository.findByPortfolioId(portfolioOf(user).getId())).isEmpty();
    }

    @Test
    void marketSellCreditsProceedsMinusCommissionAndReportsRealizedPnL() {
        User user = newUser("0.00");
        Ticker ticker = newTicker("100.00");
        holdingRepository.save(new Holding(portfolioOf(user), ticker, new BigDecimal("3"), new BigDecimal("80.00")));

        OrderResponse order = portfolioService.placeOrder(market(ticker, OrderType.SELL, "3"), user);

        assertThat(order.realizedPnL()).isEqualByComparingTo("60.00"); // (100 - 80) * 3
        assertThat(portfolioOf(user).getCashBalance()).isEqualByComparingTo("299.00"); // 300 - $1.00 minimum fee
        assertThat(holdingRepository.findByPortfolioId(portfolioOf(user).getId())).isEmpty();
    }

    @Test
    void sellingMoreThanOwnedIsRejected() {
        User user = newUser("0.00");
        Ticker ticker = newTicker("100.00");
        holdingRepository.save(new Holding(portfolioOf(user), ticker, new BigDecimal("1"), new BigDecimal("80.00")));

        assertThatThrownBy(() -> portfolioService.placeOrder(market(ticker, OrderType.SELL, "2"), user))
                .isInstanceOf(InsufficientSharesException.class);
    }
}
