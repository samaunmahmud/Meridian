package com.meridian.backend;

import com.meridian.backend.dto.OrderRequest;
import com.meridian.backend.dto.OrderResponse;
import com.meridian.backend.dto.TransactionResponse;
import com.meridian.backend.exception.InsufficientFundsException;
import com.meridian.backend.exception.InvalidRequestException;
import com.meridian.backend.model.Holding;
import com.meridian.backend.model.OrderKind;
import com.meridian.backend.model.OrderType;
import com.meridian.backend.model.SupportedCurrency;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.User;
import com.meridian.backend.service.PortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Market orders paid from / credited to a EUR wallet instead of USD cash. */
class SettlementCurrencyTest extends IntegrationTestBase {

    @Autowired PortfolioService portfolioService;

    @BeforeEach
    void eurIsWorthOnePointOneDollars() {
        setFxRate(SupportedCurrency.EUR, "1.1000"); // applied rate = 1.1 * (1 - 0.5%) = 1.0945 USD per EUR
    }

    private OrderRequest market(Ticker t, OrderType type, String qty, SupportedCurrency settlement) {
        return new OrderRequest(t.getSymbol(), type, OrderKind.MARKET, new BigDecimal(qty), null, null, settlement);
    }

    @Test
    void buyingFromAnEuroWalletChargesTheConvertedTotalAndLeavesUsdAlone() {
        User user = newUser("0.00");
        walletService.deposit(SupportedCurrency.EUR, new BigDecimal("1000.00"), user);
        Ticker ticker = newTicker("100.00");

        OrderResponse order = portfolioService.placeOrder(market(ticker, OrderType.BUY, "2", SupportedCurrency.EUR), user);

        // $200 shares + $1.00 minimum commission = $201; 201 / 1.0945 = 183.6455... rounded UP to 183.6456 EUR
        assertThat(order.settlementCurrency()).isEqualTo(SupportedCurrency.EUR);
        assertThat(order.settlementAmount()).isEqualByComparingTo("183.6456");
        assertThat(order.feeAmount()).isEqualByComparingTo("1.00"); // commission is still reported in USD
        assertThat(balance(user, SupportedCurrency.EUR)).isEqualByComparingTo("816.3544");
        assertThat(portfolioOf(user).getCashBalance()).isEqualByComparingTo("0.00");

        Holding holding = holdingRepository.findByPortfolioId(portfolioOf(user).getId()).get(0);
        assertThat(holding.getQuantity()).isEqualByComparingTo("2");
        assertThat(holding.getAvgCost()).isEqualByComparingTo("100.00"); // cost basis stays in USD

        // The ledger records the EUR that actually moved: buy + commission add up to what was charged.
        List<TransactionResponse> eurRows = portfolioService.getTransactionHistory(user).stream()
                .filter(t -> "EUR".equals(t.currency()) && t.relatedOrderId() != null).toList();
        assertThat(eurRows).hasSize(2);
        assertThat(eurRows.stream().map(TransactionResponse::amount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("-183.6456");
    }

    @Test
    void notEnoughEuroMeansNothingHappens() {
        User user = newUser("10000.00"); // plenty of USD, but the order asked to pay in EUR
        walletService.deposit(SupportedCurrency.EUR, new BigDecimal("10.00"), user);
        Ticker ticker = newTicker("100.00");

        assertThatThrownBy(() -> portfolioService.placeOrder(market(ticker, OrderType.BUY, "2", SupportedCurrency.EUR), user))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("EUR");

        assertThat(balance(user, SupportedCurrency.EUR)).isEqualByComparingTo("10.00");
        assertThat(portfolioOf(user).getCashBalance()).isEqualByComparingTo("10000.00");
        assertThat(portfolioService.getOrderHistory(user)).isEmpty();
        assertThat(holdingRepository.findByPortfolioId(portfolioOf(user).getId())).isEmpty();
    }

    @Test
    void sellingIntoAnEuroWalletCreditsTheConvertedProceedsAfterCommissionAndSpread() {
        User user = newUser("0.00");
        Ticker ticker = newTicker("100.00");
        holdingRepository.save(new Holding(portfolioOf(user), ticker, new BigDecimal("3"), new BigDecimal("80.00")));

        OrderResponse order = portfolioService.placeOrder(market(ticker, OrderType.SELL, "3", SupportedCurrency.EUR), user);

        // $300 - $1.00 commission = $299; x 0.995 / 1.10 = 270.4590... rounded DOWN
        assertThat(order.settlementAmount()).isEqualByComparingTo("270.4590");
        assertThat(order.realizedPnL()).isEqualByComparingTo("60.00");
        assertThat(balance(user, SupportedCurrency.EUR)).isEqualByComparingTo("270.4590");
        assertThat(portfolioOf(user).getCashBalance()).isEqualByComparingTo("0.00");
        assertThat(holdingRepository.findByPortfolioId(portfolioOf(user).getId())).isEmpty();
    }

    @Test
    void aSaleWorthLessThanItsCommissionCannotBeSettledInAnotherCurrency() {
        User user = newUser("0.00");
        Ticker ticker = newTicker("0.50");
        holdingRepository.save(new Holding(portfolioOf(user), ticker, new BigDecimal("1"), new BigDecimal("0.50")));

        assertThatThrownBy(() -> portfolioService.placeOrder(market(ticker, OrderType.SELL, "1", SupportedCurrency.EUR), user))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("commission");
    }

    @Test
    void limitOrdersCannotSettleInAnotherCurrency() {
        User user = newUser("1000.00");
        Ticker ticker = newTicker("100.00");

        assertThatThrownBy(() -> portfolioService.placeOrder(new OrderRequest(ticker.getSymbol(), OrderType.BUY,
                OrderKind.LIMIT, BigDecimal.ONE, new BigDecimal("90.00"), null, SupportedCurrency.EUR), user))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("settle in USD");
    }

    @Test
    void concurrentEuroBuysCannotSpendTheSameWalletTwice() throws Exception {
        User user = newUser("0.00");
        walletService.deposit(SupportedCurrency.EUR, new BigDecimal("250.00"), user);
        Ticker ticker = newTicker("100.00"); // 1 share = $101 = 92.2796 EUR

        int succeeded = runConcurrently(10, () -> portfolioService.placeOrder(market(ticker, OrderType.BUY, "1", SupportedCurrency.EUR), user));

        assertThat(succeeded).isEqualTo(2); // 250 / 92.28 = 2.7
        assertThat(balance(user, SupportedCurrency.EUR)).isBetween(new BigDecimal("65.44"), new BigDecimal("65.45"));
        assertThat(holdingRepository.findByPortfolioId(portfolioOf(user).getId()).get(0).getQuantity()).isEqualByComparingTo("2");
    }
}
