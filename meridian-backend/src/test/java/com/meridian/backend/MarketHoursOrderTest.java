package com.meridian.backend;

import com.meridian.backend.dto.OrderRequest;
import com.meridian.backend.dto.OrderResponse;
import com.meridian.backend.dto.RecurringOrderRequest;
import com.meridian.backend.mail.MailService;
import com.meridian.backend.model.AssetType;
import com.meridian.backend.model.Holding;
import com.meridian.backend.model.Order;
import com.meridian.backend.model.OrderKind;
import com.meridian.backend.model.OrderStatus;
import com.meridian.backend.model.OrderType;
import com.meridian.backend.model.RecurringFrequency;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.User;
import com.meridian.backend.service.PortfolioService;
import com.meridian.backend.service.RecurringOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Trading hours are ON here (the other tests switch them off) and the clock is under the test's
 * control. Saturday 2026-09-19 is closed; Monday 2026-09-21 at 14:00 UTC is 10:00 in New York, open.
 */
// (price staleness is not what this class is about, and the test clock is not the real one)
@TestPropertySource(properties = {"marketdata.hours-enforced=true",
        "marketdata.max-price-age-minutes=100000000", "marketdata.max-fx-rate-age-minutes=100000000"})
class MarketHoursOrderTest extends IntegrationTestBase {

    private static final Instant SATURDAY = Instant.parse("2026-09-19T15:00:00Z");
    private static final Instant MONDAY_10AM = Instant.parse("2026-09-21T14:00:00Z");

    @TestConfiguration
    static class ControlledClock {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(SATURDAY);
        }
    }

    @MockBean MailService mail;
    @Autowired MutableClock clock;
    @Autowired PortfolioService portfolioService;
    @Autowired RecurringOrderService recurringOrderService;

    @BeforeEach
    void marketIsClosed() {
        clock.set(SATURDAY);
    }

    private void openTheMarket() {
        clock.set(MONDAY_10AM);
    }

    private OrderRequest marketOrder(Ticker t, OrderType type, String qty) {
        return new OrderRequest(t.getSymbol(), type, OrderKind.MARKET, new BigDecimal(qty), null, null);
    }

    private Order stored(OrderResponse response) {
        return orderRepository.findById(response.id()).orElseThrow();
    }

    @Test
    void aMarketBuyWhileTheMarketIsClosedIsQueuedAndHoldsItsMoney() {
        User user = newUser("10000.00");
        Ticker ticker = newTicker("100.00");

        OrderResponse order = portfolioService.placeOrder(marketOrder(ticker, OrderType.BUY, "2"), user);

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.kind()).isEqualTo(OrderKind.MARKET);
        assertThat(order.price()).as("no fill price yet").isNull();
        // last price $100 + 10% cushion = $220 for two shares, plus the $1.00 minimum commission
        assertThat(portfolioOf(user).getReservedCash()).isEqualByComparingTo("221.00");
        assertThat(portfolioOf(user).getCashBalance()).isEqualByComparingTo("10000.00");
        assertThat(holdingRepository.findByPortfolioId(portfolioOf(user).getId())).isEmpty();
    }

    @Test
    void theQueuedOrderFillsAtTheFirstPriceAfterTheOpenAndReleasesWhatItDidNotNeed() {
        User user = newUser("10000.00");
        Ticker ticker = newTicker("100.00");
        OrderResponse order = portfolioService.placeOrder(marketOrder(ticker, OrderType.BUY, "2"), user);

        openTheMarket();
        portfolioService.checkPendingOrders(ticker, new BigDecimal("103.00"));

        Order filled = stored(order);
        assertThat(filled.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(filled.getPrice()).isEqualByComparingTo("103.00");
        // 2 x 103 = 206 + 1.00 commission
        assertThat(portfolioOf(user).getCashBalance()).isEqualByComparingTo("9793.00");
        assertThat(portfolioOf(user).getReservedCash()).isEqualByComparingTo("0.00");
        assertThat(holdingRepository.findByPortfolioId(portfolioOf(user).getId())).singleElement()
                .extracting(Holding::getQuantity).satisfies(q -> assertThat(q).isEqualByComparingTo("2"));
    }

    @Test
    void nothingFillsWhileTheMarketIsStillClosed() {
        User user = newUser("10000.00");
        Ticker ticker = newTicker("100.00");
        OrderResponse order = portfolioService.placeOrder(marketOrder(ticker, OrderType.BUY, "1"), user);

        portfolioService.checkPendingOrders(ticker, new BigDecimal("100.00")); // a poll on Saturday repeats Friday's close

        assertThat(stored(order).getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void aLimitOrderDoesNotFillOnAClosedMarketEvenIfTheLastCloseWouldSatisfyIt() {
        User user = newUser("10000.00");
        Ticker ticker = newTicker("100.00");
        OrderResponse order = portfolioService.placeOrder(new OrderRequest(ticker.getSymbol(), OrderType.BUY,
                OrderKind.LIMIT, new BigDecimal("1"), new BigDecimal("105.00"), null), user);

        portfolioService.checkPendingOrders(ticker, new BigDecimal("100.00"));
        assertThat(stored(order).getStatus()).isEqualTo(OrderStatus.PENDING);

        openTheMarket();
        portfolioService.checkPendingOrders(ticker, new BigDecimal("100.00"));
        assertThat(stored(order).getStatus()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void cancellingAQueuedOrderFreesItsMoney() {
        User user = newUser("10000.00");
        Ticker ticker = newTicker("100.00");
        OrderResponse order = portfolioService.placeOrder(marketOrder(ticker, OrderType.BUY, "2"), user);

        portfolioService.cancelOrder(order.id(), user);

        assertThat(stored(order).getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(portfolioOf(user).getReservedCash()).isEqualByComparingTo("0.00");
    }

    @Test
    void aQueuedSellHoldsItsSharesAndFillsAtTheOpen() {
        User user = newUser("10000.00");
        Ticker ticker = newTicker("100.00");
        openTheMarket();
        portfolioService.placeOrder(marketOrder(ticker, OrderType.BUY, "4"), user); // owns 4 shares
        clock.set(SATURDAY);

        OrderResponse sell = portfolioService.placeOrder(marketOrder(ticker, OrderType.SELL, "3"), user);

        assertThat(sell.status()).isEqualTo(OrderStatus.PENDING);
        Holding held = holdingRepository.findByPortfolioId(portfolioOf(user).getId()).get(0);
        assertThat(held.getReservedQuantity()).isEqualByComparingTo("3");

        clock.set(MONDAY_10AM);
        portfolioService.checkPendingOrders(ticker, new BigDecimal("110.00"));

        assertThat(stored(sell).getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(holdingRepository.findByPortfolioId(portfolioOf(user).getId()).get(0).getQuantity()).isEqualByComparingTo("1");
    }

    @Test
    void ifTheStockGapsBeyondWhatWasHeldTheOrderIsRejectedNotOverdrawn() {
        User user = newUser("1000.00");
        Ticker ticker = newTicker("100.00");
        // 9 shares: holds 9 x $110 + fee = $992.48 of the $1 000
        OrderResponse order = portfolioService.placeOrder(marketOrder(ticker, OrderType.BUY, "9"), user);

        openTheMarket();
        portfolioService.checkPendingOrders(ticker, new BigDecimal("130.00")); // opens 30% higher

        Order rejected = stored(order);
        assertThat(rejected.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).contains("Insufficient funds");
        assertThat(portfolioOf(user).getCashBalance()).isEqualByComparingTo("1000.00");
        assertThat(portfolioOf(user).getReservedCash()).isEqualByComparingTo("0.00");
    }

    @Test
    void cryptoIsNeverQueued() {
        User user = newUser("10000.00");
        Ticker crypto = tickerRepository.save(new Ticker("BTCX" + System.nanoTime() % 100000, "Test coin", "CRYPTO", AssetType.CRYPTO));
        setPrice(crypto, "50.00");

        OrderResponse order = portfolioService.placeOrder(marketOrder(crypto, OrderType.BUY, "1"), user);

        assertThat(order.status()).isEqualTo(OrderStatus.FILLED);
    }

    @Test
    void aRecurringBuyWaitsForTheMarketToOpen() {
        User user = newUser("1000.00");
        Ticker ticker = newTicker("100.00");
        recurringOrderService.create(
                new RecurringOrderRequest(ticker.getSymbol(), new BigDecimal("200.00"), RecurringFrequency.DAILY), user);

        recurringOrderService.runDue(); // Saturday

        List<Order> orders = orderRepository.findByPortfolioIdOrderByCreatedAtDesc(portfolioOf(user).getId());
        assertThat(orders).isEmpty();

        openTheMarket();
        recurringOrderService.runDue();

        assertThat(orderRepository.findByPortfolioIdOrderByCreatedAtDesc(portfolioOf(user).getId())).hasSize(1);
    }

    private User userWithConfirmedEmail(String cash) {
        User user = newUser(cash);
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    @Test
    void aQueuedOrderThatFillsWhileYouAreAwayIsEmailedToAConfirmedAddress() {
        User user = userWithConfirmedEmail("10000.00");
        Ticker ticker = newTicker("100.00");
        portfolioService.placeOrder(marketOrder(ticker, OrderType.BUY, "2"), user);

        openTheMarket();
        portfolioService.checkPendingOrders(ticker, new BigDecimal("103.00"));

        verify(mail).send(eq(user.getEmail()), eq("Order filled: buy 2 " + ticker.getSymbol() + " @ $103.00"),
                contains("queued market order"));
    }

    @Test
    void anUnconfirmedAddressIsNotEmailedAboutTradingActivity() {
        User user = newUser("10000.00");
        Ticker ticker = newTicker("100.00");
        portfolioService.placeOrder(marketOrder(ticker, OrderType.BUY, "2"), user);

        openTheMarket();
        portfolioService.checkPendingOrders(ticker, new BigDecimal("103.00"));

        verify(mail, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void aRejectedQueuedOrderIsEmailedWithTheReason() {
        User user = userWithConfirmedEmail("1000.00");
        Ticker ticker = newTicker("100.00");
        portfolioService.placeOrder(marketOrder(ticker, OrderType.BUY, "9"), user);

        openTheMarket();
        portfolioService.checkPendingOrders(ticker, new BigDecimal("130.00"));

        verify(mail).send(eq(user.getEmail()), eq("Order rejected: buy 9 " + ticker.getSymbol()), contains("Insufficient funds"));
    }
}
