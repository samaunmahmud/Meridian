package com.meridian.backend;

import com.meridian.backend.dto.OrderRequest;
import com.meridian.backend.dto.OrderResponse;
import com.meridian.backend.dto.WalletResponse;
import com.meridian.backend.exception.InsufficientFundsException;
import com.meridian.backend.model.Holding;
import com.meridian.backend.model.Order;
import com.meridian.backend.model.OrderKind;
import com.meridian.backend.model.OrderStatus;
import com.meridian.backend.model.OrderType;
import com.meridian.backend.model.SupportedCurrency;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.User;
import com.meridian.backend.service.PortfolioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Limit and stop-loss orders that pay from / into a EUR wallet. All amounts
 * are worked out by hand at 1 EUR = 1.10 USD, which after the 0.5% spread is
 * 1.0945 USD per EUR; a $1.00 minimum commission applies to these small trades.
 */
class PendingOrderCurrencyTest extends IntegrationTestBase {

    @Autowired PortfolioService portfolioService;

    @BeforeEach
    void eurIsWorthOnePointOneDollars() {
        setFxRate(SupportedCurrency.EUR, "1.1000");
    }

    private OrderRequest limitBuyInEur(Ticker t, String qty, String limit) {
        return new OrderRequest(t.getSymbol(), OrderType.BUY, OrderKind.LIMIT, new BigDecimal(qty),
                new BigDecimal(limit), null, SupportedCurrency.EUR);
    }

    private WalletResponse wallet(User user, SupportedCurrency currency) {
        return walletService.getWallets(user).stream().filter(w -> w.currency() == currency).findFirst().orElseThrow();
    }

    private User userWithEur(String eur) {
        User user = newUser("0.00");
        walletService.deposit(SupportedCurrency.EUR, new BigDecimal(eur), user);
        return user;
    }

    @Test
    void aLimitBuyReservesItsWorstCaseCostInTheWalletCurrencyNotInUsd() {
        User user = userWithEur("1000.00");
        Ticker ticker = newTicker("110.00");

        OrderResponse order = portfolioService.placeOrder(limitBuyInEur(ticker, "2", "100.00"), user);

        // $200 + $1.00 commission = $201; 201 / 1.0945 = 183.6455... rounded UP to 183.6456 EUR
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.settlementCurrency()).isEqualTo(SupportedCurrency.EUR);
        WalletResponse eur = wallet(user, SupportedCurrency.EUR);
        assertThat(eur.balance()).isEqualByComparingTo("1000.00");
        assertThat(eur.reserved()).isEqualByComparingTo("183.6456");
        assertThat(eur.available()).isEqualByComparingTo("816.3544");
        assertThat(portfolioOf(user).getReservedCash()).isEqualByComparingTo("0"); // USD untouched
    }

    @Test
    void whenItFillsItPaysFromTheWalletAtTheFillPriceAndReleasesTheReservation() {
        User user = userWithEur("1000.00");
        Ticker ticker = newTicker("110.00");
        OrderResponse order = portfolioService.placeOrder(limitBuyInEur(ticker, "2", "100.00"), user);

        portfolioService.checkPendingOrders(ticker, new BigDecimal("95.00"));

        // filled at $95: $190 + $1.00 = $191; 191 / 1.0945 = 174.5089... rounded UP to 174.5090 EUR
        Order filled = orderRepository.findById(order.id()).orElseThrow();
        assertThat(filled.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(filled.getPrice()).isEqualByComparingTo("95.00");
        assertThat(filled.getSettlementCurrency()).isEqualTo(SupportedCurrency.EUR);
        assertThat(filled.getSettlementAmount()).isEqualByComparingTo("174.5090");
        WalletResponse eur = wallet(user, SupportedCurrency.EUR);
        assertThat(eur.balance()).isEqualByComparingTo("825.4910");
        assertThat(eur.reserved()).isEqualByComparingTo("0");
        assertThat(portfolioOf(user).getCashBalance()).isEqualByComparingTo("0.00");
        assertThat(holdingRepository.findByPortfolioId(portfolioOf(user).getId()).get(0).getQuantity()).isEqualByComparingTo("2");
    }

    @Test
    void cancellingReleasesTheReservedWalletMoney() {
        User user = userWithEur("1000.00");
        Ticker ticker = newTicker("110.00");
        OrderResponse order = portfolioService.placeOrder(limitBuyInEur(ticker, "2", "100.00"), user);

        portfolioService.cancelOrder(order.id(), user);

        WalletResponse eur = wallet(user, SupportedCurrency.EUR);
        assertThat(eur.balance()).isEqualByComparingTo("1000.00");
        assertThat(eur.reserved()).isEqualByComparingTo("0");
    }

    @Test
    void moneyAlreadyReservedByOneOrderCannotBePromisedToAnother() {
        User user = userWithEur("200.00"); // enough for one order (183.6456), not two
        Ticker ticker = newTicker("110.00");
        portfolioService.placeOrder(limitBuyInEur(ticker, "2", "100.00"), user);

        assertThatThrownBy(() -> portfolioService.placeOrder(limitBuyInEur(ticker, "2", "100.00"), user))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("EUR");

        assertThat(wallet(user, SupportedCurrency.EUR).reserved()).isEqualByComparingTo("183.6456");
        assertThat(orderRepository.findByPortfolioIdAndStatusOrderByCreatedAtDesc(portfolioOf(user).getId(), OrderStatus.PENDING)).hasSize(1);
    }

    @Test
    void reservedWalletMoneyCannotBeWithdrawnOrConverted() {
        User user = userWithEur("200.00");
        Ticker ticker = newTicker("110.00");
        portfolioService.placeOrder(limitBuyInEur(ticker, "2", "100.00"), user); // 183.6456 reserved, 16.3544 free

        assertThatThrownBy(() -> walletService.withdraw(SupportedCurrency.EUR, new BigDecimal("50.00"), user))
                .isInstanceOf(InsufficientFundsException.class);
        assertThatThrownBy(() -> walletService.convert(new com.meridian.backend.dto.ConvertRequest(
                SupportedCurrency.EUR, SupportedCurrency.USD, new BigDecimal("50.00")), user))
                .isInstanceOf(InsufficientFundsException.class);

        walletService.withdraw(SupportedCurrency.EUR, new BigDecimal("16.00"), user); // the free part is still usable
        assertThat(wallet(user, SupportedCurrency.EUR).balance()).isEqualByComparingTo("184.00");
    }

    @Test
    void reservedUsdCashCannotBeWithdrawnThroughTheWalletEither() {
        User user = newUser("1000.00");
        Ticker ticker = newTicker("110.00");
        portfolioService.placeOrder(new OrderRequest(ticker.getSymbol(), OrderType.BUY, OrderKind.LIMIT,
                new BigDecimal("2"), new BigDecimal("100.00"), null), user); // reserves $201

        assertThatThrownBy(() -> walletService.withdraw(SupportedCurrency.USD, new BigDecimal("900.00"), user))
                .isInstanceOf(InsufficientFundsException.class);

        walletService.withdraw(SupportedCurrency.USD, new BigDecimal("799.00"), user);
        assertThat(portfolioOf(user).getCashBalance()).isEqualByComparingTo("201.00");
    }

    @Test
    void ifTheExchangeRateMovesAgainstTheUserAndTheWalletCannotCoverItTheOrderIsRejected() {
        User user = userWithEur("183.6456"); // exactly what the order reserves at today's rate
        Ticker ticker = newTicker("110.00");
        OrderResponse order = portfolioService.placeOrder(limitBuyInEur(ticker, "2", "100.00"), user);

        setFxRate(SupportedCurrency.EUR, "1.0000"); // EUR weakens: $191 now costs 191 / 0.995 = 191.9598 EUR
        portfolioService.checkPendingOrders(ticker, new BigDecimal("95.00"));

        Order rejected = orderRepository.findById(order.id()).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).contains("EUR");
        WalletResponse eur = wallet(user, SupportedCurrency.EUR);
        assertThat(eur.balance()).isEqualByComparingTo("183.6456"); // nothing was taken
        assertThat(eur.reserved()).isEqualByComparingTo("0");       // and the reservation is freed
        assertThat(holdingRepository.findByPortfolioId(portfolioOf(user).getId())).isEmpty();
    }

    @Test
    void aStopLossSellPaysIntoTheWalletChosenWhenItWasPlaced() {
        User user = newUser("0.00");
        Ticker ticker = newTicker("100.00");
        holdingRepository.save(new Holding(portfolioOf(user), ticker, new BigDecimal("3"), new BigDecimal("80.00")));
        OrderResponse order = portfolioService.placeOrder(new OrderRequest(ticker.getSymbol(), OrderType.SELL,
                OrderKind.STOP_LOSS, new BigDecimal("3"), null, new BigDecimal("90.00"), SupportedCurrency.EUR), user);
        assertThat(order.settlementCurrency()).isEqualTo(SupportedCurrency.EUR);

        portfolioService.checkPendingOrders(ticker, new BigDecimal("85.00"));

        // $255 - $1.00 commission = $254; x 0.995 / 1.10 = 229.7545... rounded DOWN
        Order filled = orderRepository.findById(order.id()).orElseThrow();
        assertThat(filled.getStatus()).isEqualTo(OrderStatus.FILLED);
        assertThat(filled.getSettlementAmount()).isEqualByComparingTo("229.7545");
        assertThat(wallet(user, SupportedCurrency.EUR).balance()).isEqualByComparingTo("229.7545");
        assertThat(portfolioOf(user).getCashBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void usdLimitOrdersAreUnchanged() {
        User user = newUser("1000.00");
        Ticker ticker = newTicker("110.00");

        OrderResponse order = portfolioService.placeOrder(new OrderRequest(ticker.getSymbol(), OrderType.BUY,
                OrderKind.LIMIT, new BigDecimal("2"), new BigDecimal("100.00"), null), user);

        assertThat(order.settlementCurrency()).isNull();
        assertThat(portfolioOf(user).getReservedCash()).isEqualByComparingTo("201.00");
        portfolioService.checkPendingOrders(ticker, new BigDecimal("95.00"));
        assertThat(portfolioOf(user).getCashBalance()).isEqualByComparingTo("809.00"); // 1000 - 190 - 1.00
        assertThat(portfolioOf(user).getReservedCash()).isEqualByComparingTo("0.00");
    }

    @Test
    void concurrentLimitBuysCannotReserveTheSameWalletMoneyTwice() throws Exception {
        User user = userWithEur("250.00");
        Ticker ticker = newTicker("110.00");

        // each needs 101 / 1.0945 = 92.2796 EUR reserved: two fit in 250, three do not
        int placed = runConcurrently(10, () -> portfolioService.placeOrder(limitBuyInEur(ticker, "1", "100.00"), user));

        assertThat(placed).isEqualTo(2);
        WalletResponse eur = wallet(user, SupportedCurrency.EUR);
        assertThat(eur.reserved()).isEqualByComparingTo("184.5592");
        assertThat(eur.available()).isEqualByComparingTo("65.4408");
    }
}
