package com.meridian.backend;

import com.meridian.backend.dto.OrderRequest;
import com.meridian.backend.dto.OrderResponse;
import com.meridian.backend.exception.InsufficientFundsException;
import com.meridian.backend.exception.InsufficientSharesException;
import com.meridian.backend.model.Holding;
import com.meridian.backend.model.Order;
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
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PendingOrderTest extends IntegrationTestBase {

    @Autowired PortfolioService portfolioService;

    private OrderRequest limitBuy(Ticker t, String qty, String limit) {
        return new OrderRequest(t.getSymbol(), OrderType.BUY, OrderKind.LIMIT, new BigDecimal(qty), new BigDecimal(limit), null);
    }

    private OrderRequest stopSell(Ticker t, String qty, String stop) {
        return new OrderRequest(t.getSymbol(), OrderType.SELL, OrderKind.STOP_LOSS, new BigDecimal(qty), null, new BigDecimal(stop));
    }

    private Order reload(OrderResponse order) {
        return orderRepository.findById(order.id()).orElseThrow();
    }

    @Test
    void limitBuyMustAffordItsOwnCommission() {
        User user = newUser("1000.00");
        Ticker ticker = newTicker("100.00");

        // 10 x $100 = $1,000 for the shares alone, plus a $2.50 commission: not affordable.
        assertThatThrownBy(() -> portfolioService.placeOrder(limitBuy(ticker, "10", "100.00"), user))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("including fees");
        assertThat(portfolioOf(user).getReservedCash()).isEqualByComparingTo("0");
    }

    @Test
    void limitBuyReservesPricePlusCommission() {
        User user = newUser("1000.00");
        Ticker ticker = newTicker("100.00");

        portfolioService.placeOrder(limitBuy(ticker, "9", "100.00"), user);

        Portfolio portfolio = portfolioOf(user);
        assertThat(portfolio.getReservedCash()).isEqualByComparingTo("902.25"); // 900 + 0.25% commission
        assertThat(portfolio.getAvailableCash()).isEqualByComparingTo("97.75");
    }

    @Test
    void anOrderThatReservedAllTheCashStillFillsAtItsLimit() {
        // This is the "stuck order" scenario: all cash reserved, then the fill needs cost + fee.
        User user = newUser("902.25");
        Ticker ticker = newTicker("100.00");
        OrderResponse placed = portfolioService.placeOrder(limitBuy(ticker, "9", "100.00"), user);
        assertThat(portfolioOf(user).getAvailableCash()).isEqualByComparingTo("0.00");

        portfolioService.checkPendingOrders(ticker, new BigDecimal("100.00"));

        assertThat(reload(placed).getStatus()).isEqualTo(OrderStatus.FILLED);
        Portfolio portfolio = portfolioOf(user);
        assertThat(portfolio.getCashBalance()).isEqualByComparingTo("0.00");
        assertThat(portfolio.getReservedCash()).isEqualByComparingTo("0.00");
    }

    @Test
    void limitBuyFillsAtTheBetterPriceAndReleasesTheRestOfTheReservation() {
        User user = newUser("1000.00");
        Ticker ticker = newTicker("100.00");
        OrderResponse placed = portfolioService.placeOrder(limitBuy(ticker, "9", "100.00"), user);

        portfolioService.checkPendingOrders(ticker, new BigDecimal("105.00")); // above the limit: no fill
        assertThat(reload(placed).getStatus()).isEqualTo(OrderStatus.PENDING);

        portfolioService.checkPendingOrders(ticker, new BigDecimal("95.00"));

        Order filled = reload(placed);
        assertThat(filled.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(filled.getPrice()).isEqualByComparingTo("95.00");
        Portfolio portfolio = portfolioOf(user);
        // 9 x 95 = 855.00, commission 0.25% = 2.1375 -> 1000 - 857.1375
        assertThat(portfolio.getCashBalance()).isEqualByComparingTo("142.8625");
        assertThat(portfolio.getReservedCash()).isEqualByComparingTo("0");
    }

    @Test
    void cancellingReleasesTheWholeReservation() {
        User user = newUser("1000.00");
        Ticker ticker = newTicker("100.00");
        OrderResponse placed = portfolioService.placeOrder(limitBuy(ticker, "9", "100.00"), user);

        portfolioService.cancelOrder(placed.id(), user);

        assertThat(portfolioOf(user).getReservedCash()).isEqualByComparingTo("0");
        assertThat(reload(placed).getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void aCancelledOrderIsNeverFilledLater() {
        User user = newUser("1000.00");
        Ticker ticker = newTicker("100.00");
        OrderResponse placed = portfolioService.placeOrder(limitBuy(ticker, "5", "100.00"), user);
        portfolioService.cancelOrder(placed.id(), user);

        portfolioService.checkPendingOrders(ticker, new BigDecimal("90.00"));

        assertThat(reload(placed).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(portfolioOf(user).getCashBalance()).isEqualByComparingTo("1000.00");
        assertThat(holdingRepository.findByPortfolioId(portfolioOf(user).getId())).isEmpty();
    }

    @Test
    void oneUnfillableOrderIsRejectedAndDoesNotBlockOthers() {
        Ticker ticker = newTicker("100.00");

        // User A has an order from before commission was reserved: it holds exactly the
        // price of the shares ($1,000) and so cannot pay the $2.50 fee when it triggers.
        User a = newUser("1000.00");
        Portfolio pa = portfolioOf(a);
        pa.setReservedCash(new BigDecimal("1000.00"));
        portfolioRepository.save(pa);
        Order legacy = orderRepository.save(new Order(pa, ticker, OrderType.BUY, OrderKind.LIMIT,
                new BigDecimal("10"), new BigDecimal("100.00"), null, Instant.now()));

        // User B has a normal order on the same ticker.
        User b = newUser("1000.00");
        OrderResponse normal = portfolioService.placeOrder(limitBuy(ticker, "5", "100.00"), b);

        portfolioService.checkPendingOrders(ticker, new BigDecimal("100.00"));

        assertThat(reload(normal).getStatus()).isEqualTo(OrderStatus.FILLED);

        Order rejected = orderRepository.findById(legacy.getId()).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).contains("Insufficient funds");
        Portfolio afterA = portfolioOf(a);
        assertThat(afterA.getReservedCash()).isEqualByComparingTo("0");
        assertThat(afterA.getCashBalance()).isEqualByComparingTo("1000.00"); // nothing was spent

        // ...and it is not retried: a second tick leaves everything as it is.
        portfolioService.checkPendingOrders(ticker, new BigDecimal("100.00"));
        assertThat(orderRepository.findById(legacy.getId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.REJECTED);
    }

    @Test
    void stopLossSellFillsOnlyWhenThePriceReachesTheStop() {
        User user = newUser("0.00");
        Ticker ticker = newTicker("100.00");
        holdingRepository.save(new Holding(portfolioOf(user), ticker, new BigDecimal("4"), new BigDecimal("70.00")));
        OrderResponse placed = portfolioService.placeOrder(stopSell(ticker, "4", "90.00"), user);

        portfolioService.checkPendingOrders(ticker, new BigDecimal("95.00"));
        assertThat(reload(placed).getStatus()).isEqualTo(OrderStatus.PENDING);

        portfolioService.checkPendingOrders(ticker, new BigDecimal("89.00"));

        assertThat(reload(placed).getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(portfolioOf(user).getCashBalance()).isEqualByComparingTo("355.00"); // 4 x 89 = 356 - $1.00 minimum fee
        assertThat(holdingRepository.findByPortfolioId(portfolioOf(user).getId())).isEmpty();
    }

    @Test
    void sharesReservedByAPendingSellCannotBeSoldTwice() {
        User user = newUser("0.00");
        Ticker ticker = newTicker("100.00");
        holdingRepository.save(new Holding(portfolioOf(user), ticker, new BigDecimal("4"), new BigDecimal("70.00")));
        portfolioService.placeOrder(stopSell(ticker, "4", "90.00"), user);

        assertThatThrownBy(() -> portfolioService.placeOrder(
                new OrderRequest(ticker.getSymbol(), OrderType.SELL, OrderKind.MARKET, BigDecimal.ONE, null, null), user))
                .isInstanceOf(InsufficientSharesException.class);
    }
}
