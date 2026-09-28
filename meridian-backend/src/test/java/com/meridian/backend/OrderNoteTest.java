package com.meridian.backend;

import com.meridian.backend.dto.OrderRequest;
import com.meridian.backend.dto.OrderResponse;
import com.meridian.backend.exception.InvalidRequestException;
import com.meridian.backend.exception.OrderNotFoundException;
import com.meridian.backend.model.OrderKind;
import com.meridian.backend.model.OrderType;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.User;
import com.meridian.backend.service.PortfolioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderNoteTest extends IntegrationTestBase {

    @Autowired PortfolioService portfolioService;

    private OrderResponse buy(User user, Ticker ticker) {
        return portfolioService.placeOrder(new OrderRequest(ticker.getSymbol(), OrderType.BUY, OrderKind.MARKET,
                BigDecimal.ONE, null, null), user);
    }

    @Test
    void aNoteIsSavedTrimmedShownInHistoryAndClearedWhenBlank() {
        User user = newUser("1000.00");
        OrderResponse order = buy(user, newTicker("100.00"));

        OrderResponse noted = portfolioService.setNote(order.id(), "  Earnings next week  ", user);
        assertThat(noted.note()).isEqualTo("Earnings next week");
        assertThat(portfolioService.getOrderHistory(user).get(0).note()).isEqualTo("Earnings next week");

        assertThat(portfolioService.setNote(order.id(), "   ", user).note()).isNull();
        assertThat(portfolioService.getOrderHistory(user).get(0).note()).isNull();
    }

    @Test
    void notesAreLimitedInLengthAndOnlyForTheOwner() {
        User owner = newUser("1000.00");
        User stranger = newUser("1000.00");
        OrderResponse order = buy(owner, newTicker("100.00"));

        assertThatThrownBy(() -> portfolioService.setNote(order.id(), "x".repeat(501), owner))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> portfolioService.setNote(order.id(), "mine now", stranger))
                .isInstanceOf(OrderNotFoundException.class);
        assertThat(portfolioService.getOrderHistory(owner).get(0).note()).isNull();
    }
}
