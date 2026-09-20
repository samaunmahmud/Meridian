package com.meridian.backend;

import com.meridian.backend.dto.ConvertRequest;
import com.meridian.backend.dto.OrderRequest;
import com.meridian.backend.dto.OrderResponse;
import com.meridian.backend.dto.RecurringOrderRequest;
import com.meridian.backend.exception.StalePriceException;
import com.meridian.backend.model.FxRate;
import com.meridian.backend.model.OrderKind;
import com.meridian.backend.model.OrderStatus;
import com.meridian.backend.model.OrderType;
import com.meridian.backend.model.PriceHistory;
import com.meridian.backend.model.RecurringFrequency;
import com.meridian.backend.model.SupportedCurrency;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.User;
import com.meridian.backend.repository.RecurringOrderRepository;
import com.meridian.backend.service.PortfolioService;
import com.meridian.backend.service.RecurringOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What happens when the price feed has been down: anything that EXECUTES at a price or exchange
 * rate refuses one that is too old, while showing the last known value keeps working.
 * The limit is set to 60 minutes here, so 5 minutes is fresh and 2 hours is stale.
 */
@TestPropertySource(properties = {"marketdata.max-price-age-minutes=60", "marketdata.max-fx-rate-age-minutes=60"})
class StaleDataTest extends IntegrationTestBase {

    @Autowired PortfolioService portfolioService;
    @Autowired RecurringOrderService recurringOrderService;
    @Autowired RecurringOrderRepository recurringOrderRepository;

    @BeforeEach
    void eurIsWorthOnePointOneDollars() {
        setFxRate(SupportedCurrency.EUR, "1.1000");
    }

    /** Makes the newest stored price of this ticker `age` old (the "current price" is the newest one). */
    private void agePrice(Ticker ticker, Duration age) {
        PriceHistory latest = priceHistoryRepository.findFirstByTickerIdOrderByRecordedAtDesc(ticker.getId()).orElseThrow();
        latest.setRecordedAt(Instant.now().minus(age));
        priceHistoryRepository.save(latest);
    }

    private void ageFxRate(SupportedCurrency currency, Duration age) {
        FxRate fx = fxRateRepository.findByBaseCurrencyAndQuoteCurrency(currency, SupportedCurrency.USD).orElseThrow();
        fx.setUpdatedAt(Instant.now().minus(age));
        fxRateRepository.save(fx);
    }

    private OrderRequest marketBuy(Ticker t, String qty) {
        return new OrderRequest(t.getSymbol(), OrderType.BUY, OrderKind.MARKET, new BigDecimal(qty), null, null);
    }

    // --- market orders

    @Test
    void aMarketOrderOnARecentPriceStillFills() {
        User user = newUser("10000.00");
        Ticker ticker = newTicker("100.00");
        agePrice(ticker, Duration.ofMinutes(5));

        OrderResponse order = portfolioService.placeOrder(marketBuy(ticker, "1"), user);

        assertThat(order.status()).isEqualTo(OrderStatus.FILLED);
        assertThat(order.price()).isEqualByComparingTo("100.00");
    }

    @Test
    void aMarketOrderRefusesAPriceThatIsHoursOldAndChangesNothing() {
        User user = newUser("10000.00");
        Ticker ticker = newTicker("100.00");
        agePrice(ticker, Duration.ofHours(2));

        assertThatThrownBy(() -> portfolioService.placeOrder(marketBuy(ticker, "1"), user))
                .isInstanceOf(StalePriceException.class)
                .hasMessageContaining(ticker.getSymbol())
                .hasMessageContaining("2 hours old")
                .hasMessageContaining("paused");

        assertThat(portfolioOf(user).getCashBalance()).isEqualByComparingTo("10000.00");
        assertThat(orderRepository.findByPortfolioIdOrderByCreatedAtDesc(portfolioOf(user).getId())).isEmpty();
        assertThat(holdingRepository.findByPortfolioId(portfolioOf(user).getId())).isEmpty();
    }

    @Test
    void theMarketOrderWorksAgainAsSoonAsAFreshPriceArrives() {
        User user = newUser("10000.00");
        Ticker ticker = newTicker("100.00");
        agePrice(ticker, Duration.ofHours(2));
        assertThatThrownBy(() -> portfolioService.placeOrder(marketBuy(ticker, "1"), user))
                .isInstanceOf(StalePriceException.class);

        setPrice(ticker, "101.00"); // the feed is back

        assertThat(portfolioService.placeOrder(marketBuy(ticker, "1"), user).price()).isEqualByComparingTo("101.00");
    }

    @Test
    void theLastKnownPriceIsStillUsedToShowWhatHoldingsAreWorth() {
        User user = newUser("10000.00");
        Ticker ticker = newTicker("100.00");
        portfolioService.placeOrder(marketBuy(ticker, "2"), user);
        agePrice(ticker, Duration.ofHours(6));

        var valuation = portfolioService.getPortfolioValuation(user);

        assertThat(valuation.holdings()).hasSize(1);
        assertThat(valuation.holdingsValue()).isEqualByComparingTo("200.00");
    }

    // --- currency conversion

    @Test
    void aConversionOnARecentRateStillWorks() {
        User user = newUser("1000.00");
        ageFxRate(SupportedCurrency.EUR, Duration.ofMinutes(5));

        assertThatCode(() -> walletService.convert(
                new ConvertRequest(SupportedCurrency.USD, SupportedCurrency.EUR, new BigDecimal("100")), user))
                .doesNotThrowAnyException();
    }

    @Test
    void aConversionRefusesAnExchangeRateThatIsHoursOld() {
        User user = newUser("1000.00");
        ageFxRate(SupportedCurrency.EUR, Duration.ofHours(6));

        assertThatThrownBy(() -> walletService.convert(
                new ConvertRequest(SupportedCurrency.USD, SupportedCurrency.EUR, new BigDecimal("100")), user))
                .isInstanceOf(StalePriceException.class)
                .hasMessageContaining("EUR/USD exchange rate")
                .hasMessageContaining("6 hours old");
        assertThat(balance(user, SupportedCurrency.USD)).isEqualByComparingTo("1000.00");
    }

    // --- recurring buys

    @Test
    void aRecurringBuyIsPostponedNotExecutedOnAStalePriceAndRunsOnceThePriceIsFresh() {
        User user = newUser("1000.00");
        Ticker ticker = newTicker("100.00");
        var created = recurringOrderService.create(
                new RecurringOrderRequest(ticker.getSymbol(), new BigDecimal("200.00"), RecurringFrequency.DAILY), user);
        agePrice(ticker, Duration.ofHours(3));

        recurringOrderService.runDue();

        assertThat(orderRepository.findByPortfolioIdOrderByCreatedAtDesc(portfolioOf(user).getId())).isEmpty();
        assertThat(portfolioOf(user).getCashBalance()).isEqualByComparingTo("1000.00");
        // still due: it is retried at the next run, not skipped for a whole day
        assertThat(recurringOrderRepository.findByPortfolioIdOrderByCreatedAtDesc(portfolioOf(user).getId()).get(0).getNextRunAt())
                .isEqualTo(created.nextRunAt());

        setPrice(ticker, "100.00");
        recurringOrderService.runDue();

        assertThat(orderRepository.findByPortfolioIdOrderByCreatedAtDesc(portfolioOf(user).getId())).hasSize(1);
    }

    // --- pending orders

    @Test
    void aPendingOrderIsNotRejectedWhenTheExchangeRateItNeedsIsStaleItWaitsForTheNextTick() {
        User user = newUser("0.00");
        walletService.deposit(SupportedCurrency.EUR, new BigDecimal("1000.00"), user);
        Ticker ticker = newTicker("110.00");
        OrderResponse order = portfolioService.placeOrder(new OrderRequest(ticker.getSymbol(), OrderType.BUY, OrderKind.LIMIT,
                new BigDecimal("1"), new BigDecimal("100.00"), null, SupportedCurrency.EUR), user);
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);

        ageFxRate(SupportedCurrency.EUR, Duration.ofHours(6));
        portfolioService.checkPendingOrders(ticker, new BigDecimal("99.00")); // the price now satisfies the limit

        assertThat(orderRepository.findById(order.id()).orElseThrow().getStatus())
                .as("a temporary feed problem must not permanently reject the order")
                .isEqualTo(OrderStatus.PENDING);

        setFxRate(SupportedCurrency.EUR, "1.1000"); // the rate feed is back
        portfolioService.checkPendingOrders(ticker, new BigDecimal("99.00"));

        assertThat(orderRepository.findById(order.id()).orElseThrow().getStatus()).isEqualTo(OrderStatus.FILLED);
    }
}
