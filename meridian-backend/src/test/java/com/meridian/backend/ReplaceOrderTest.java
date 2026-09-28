package com.meridian.backend;

import com.meridian.backend.dto.OrderRequest;
import com.meridian.backend.dto.OrderResponse;
import com.meridian.backend.dto.ReplaceOrderRequest;
import com.meridian.backend.exception.InsufficientFundsException;
import com.meridian.backend.exception.InvalidOrderStateException;
import com.meridian.backend.exception.OrderNotFoundException;
import com.meridian.backend.model.Holding;
import com.meridian.backend.model.OrderKind;
import com.meridian.backend.model.OrderStatus;
import com.meridian.backend.model.OrderType;
import com.meridian.backend.model.Portfolio;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.User;
import com.meridian.backend.service.PortfolioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReplaceOrderTest extends IntegrationTestBase {

    @Autowired PortfolioService portfolioService;

    private OrderResponse limitBuy(User user, Ticker t, String qty, String limit) {
        return portfolioService.placeOrder(new OrderRequest(t.getSymbol(), OrderType.BUY, OrderKind.LIMIT,
                new BigDecimal(qty), new BigDecimal(limit), null), user);
    }

    private static ReplaceOrderRequest terms(String qty, String limit, String stop, String trail) {
        return new ReplaceOrderRequest(new BigDecimal(qty), limit == null ? null : new BigDecimal(limit),
                stop == null ? null : new BigDecimal(stop), trail == null ? null : new BigDecimal(trail));
    }

    private OrderStatus statusOf(OrderResponse order) {
        return orderRepository.findById(order.id()).orElseThrow().getStatus();
    }

    @Test
    void aLimitBuyIsReplacedAndTheReservationFollowsTheNewTerms() {
        User user = newUser("1000.00");
        Ticker ticker = newTicker("100.00");
        OrderResponse original = limitBuy(user, ticker, "5", "90.00");
        assertThat(portfolioOf(user).getReservedCash()).isEqualByComparingTo("451.125"); // 450 + 0.25%

        OrderResponse replaced = portfolioService.replaceOrder(original.id(), terms("8", "95.00", null, null), user);

        assertThat(replaced.id()).isNotEqualTo(original.id());
        assertThat(replaced.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(replaced.quantity()).isEqualByComparingTo("8");
        assertThat(replaced.limitPrice()).isEqualByComparingTo("95.00");
        assertThat(statusOf(original)).isEqualTo(OrderStatus.CANCELLED);
        assertThat(portfolioOf(user).getReservedCash()).isEqualByComparingTo("761.90"); // 760 + 0.25%
    }

    @Test
    void ifTheNewOrderCantBePlacedTheOriginalIsLeftExactlyAsItWas() {
        User user = newUser("1000.00");
        Ticker ticker = newTicker("100.00");
        OrderResponse original = limitBuy(user, ticker, "5", "90.00");

        assertThatThrownBy(() -> portfolioService.replaceOrder(original.id(), terms("50", "90.00", null, null), user))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(statusOf(original)).isEqualTo(OrderStatus.PENDING);
        Portfolio portfolio = portfolioOf(user);
        assertThat(portfolio.getReservedCash()).isEqualByComparingTo("451.125");
        assertThat(portfolioService.getOpenOrders(user)).extracting(OrderResponse::id).containsExactly(original.id());
    }

    @Test
    void aStopLossSellCanTakeMoreOfTheSharesItAlreadyHeld() {
        User user = newUser("0.00");
        Ticker ticker = newTicker("100.00");
        holdingRepository.save(new Holding(portfolioOf(user), ticker, new BigDecimal("10"), new BigDecimal("80.00")));
        OrderResponse original = portfolioService.placeOrder(new OrderRequest(ticker.getSymbol(), OrderType.SELL,
                OrderKind.STOP_LOSS, new BigDecimal("10"), null, new BigDecimal("90.00")), user);

        // All 10 are held by the original; the replacement can use them because they are released first.
        OrderResponse replaced = portfolioService.replaceOrder(original.id(), terms("10", null, "85.00", null), user);

        assertThat(replaced.stopPrice()).isEqualByComparingTo("85.00");
        Holding holding = holdingRepository.findByPortfolioIdAndTickerId(portfolioOf(user).getId(), ticker.getId()).orElseThrow();
        assertThat(holding.getReservedQuantity()).isEqualByComparingTo("10");
    }

    @Test
    void aTrailingStopRestartsFromTheCurrentPriceWithTheNewTrail() {
        User user = newUser("0.00");
        Ticker ticker = newTicker("100.00");
        holdingRepository.save(new Holding(portfolioOf(user), ticker, new BigDecimal("3"), new BigDecimal("80.00")));
        OrderResponse original = portfolioService.placeOrder(new OrderRequest(ticker.getSymbol(), OrderType.SELL,
                OrderKind.TRAILING_STOP, new BigDecimal("3"), null, null, null, new BigDecimal("10")), user);
        setPrice(ticker, "120.00");

        OrderResponse replaced = portfolioService.replaceOrder(original.id(), terms("2", null, null, "5"), user);

        assertThat(replaced.stopPrice()).isEqualByComparingTo("114.00");
        assertThat(replaced.trailPercent()).isEqualByComparingTo("5");
    }

    @Test
    void onlyTheOwnersPendingLimitOrStopOrdersCanBeChanged() {
        User owner = newUser("1000.00");
        User stranger = newUser("1000.00");
        Ticker ticker = newTicker("100.00");
        OrderResponse order = limitBuy(owner, ticker, "1", "90.00");

        assertThatThrownBy(() -> portfolioService.replaceOrder(order.id(), terms("1", "91.00", null, null), stranger))
                .isInstanceOf(OrderNotFoundException.class);

        portfolioService.cancelOrder(order.id(), owner);
        assertThatThrownBy(() -> portfolioService.replaceOrder(order.id(), terms("1", "91.00", null, null), owner))
                .isInstanceOf(InvalidOrderStateException.class);
    }
}
