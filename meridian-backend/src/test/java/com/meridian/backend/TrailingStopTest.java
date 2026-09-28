package com.meridian.backend;

import com.meridian.backend.dto.OrderRequest;
import com.meridian.backend.dto.OrderResponse;
import com.meridian.backend.exception.InsufficientSharesException;
import com.meridian.backend.exception.InvalidRequestException;
import com.meridian.backend.model.Holding;
import com.meridian.backend.model.Order;
import com.meridian.backend.model.OrderKind;
import com.meridian.backend.model.OrderStatus;
import com.meridian.backend.model.OrderType;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.User;
import com.meridian.backend.service.PortfolioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrailingStopTest extends IntegrationTestBase {

    @Autowired PortfolioService portfolioService;

    private OrderRequest trailingSell(Ticker t, String qty, String percent) {
        return new OrderRequest(t.getSymbol(), OrderType.SELL, OrderKind.TRAILING_STOP, new BigDecimal(qty),
                null, null, null, percent == null ? null : new BigDecimal(percent));
    }

    private User ownerOf(Ticker ticker, String quantity, String avgCost) {
        User user = newUser("0.00");
        holdingRepository.save(new Holding(portfolioOf(user), ticker, new BigDecimal(quantity), new BigDecimal(avgCost)));
        return user;
    }

    private Order reload(OrderResponse order) {
        return orderRepository.findById(order.id()).orElseThrow();
    }

    private void tick(Ticker ticker, String price) {
        setPrice(ticker, price);
        portfolioService.checkPendingOrders(ticker, new BigDecimal(price));
    }

    @Test
    void theStopStartsBelowThePriceFollowsItUpAndNeverDown() {
        Ticker ticker = newTicker("100.00");
        User user = ownerOf(ticker, "5", "80.00");

        OrderResponse placed = portfolioService.placeOrder(trailingSell(ticker, "5", "10"), user);
        assertThat(placed.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(placed.stopPrice()).isEqualByComparingTo("90.00");
        assertThat(placed.trailPercent()).isEqualByComparingTo("10");
        Holding holding = holdingRepository.findByPortfolioIdAndTickerId(portfolioOf(user).getId(), ticker.getId()).orElseThrow();
        assertThat(holding.getReservedQuantity()).isEqualByComparingTo("5"); // shares are held, as for a stop-loss

        tick(ticker, "120.00");
        assertThat(reload(placed).getStopPrice()).isEqualByComparingTo("108.00");
        tick(ticker, "110.00"); // a dip that stays above the stop leaves it where it is
        assertThat(reload(placed).getStopPrice()).isEqualByComparingTo("108.00");
        assertThat(reload(placed).getStatus()).isEqualTo(OrderStatus.PENDING);

        tick(ticker, "107.50"); // below the raised stop: sold
        Order filled = reload(placed);
        assertThat(filled.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(filled.getPrice()).isEqualByComparingTo("107.50");
        assertThat(portfolioOf(user).getCashBalance()).isEqualByComparingTo("536.1562"); // 537.50 - 1.3438 commission (0.25%)
        assertThat(holdingRepository.findByPortfolioId(portfolioOf(user).getId())).isEmpty();
    }

    @Test
    void cancellingReleasesTheShares() {
        Ticker ticker = newTicker("50.00");
        User user = ownerOf(ticker, "4", "40.00");
        OrderResponse placed = portfolioService.placeOrder(trailingSell(ticker, "4", "5"), user);

        portfolioService.cancelOrder(placed.id(), user);

        Holding holding = holdingRepository.findByPortfolioIdAndTickerId(portfolioOf(user).getId(), ticker.getId()).orElseThrow();
        assertThat(holding.getReservedQuantity()).isEqualByComparingTo("0");
        tick(ticker, "10.00");
        assertThat(reload(placed).getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void onlySellsWithASensibleTrailAndEnoughSharesAreAccepted() {
        Ticker ticker = newTicker("50.00");
        User user = ownerOf(ticker, "2", "40.00");

        assertThatThrownBy(() -> portfolioService.placeOrder(new OrderRequest(ticker.getSymbol(), OrderType.BUY,
                OrderKind.TRAILING_STOP, BigDecimal.ONE, null, null, null, new BigDecimal("5")), user))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("sell side");
        assertThatThrownBy(() -> portfolioService.placeOrder(trailingSell(ticker, "1", null), user))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("trail");
        assertThatThrownBy(() -> portfolioService.placeOrder(trailingSell(ticker, "1", "0.1"), user))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> portfolioService.placeOrder(trailingSell(ticker, "1", "75"), user))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> portfolioService.placeOrder(trailingSell(ticker, "3", "5"), user))
                .isInstanceOf(InsufficientSharesException.class);
    }
}
