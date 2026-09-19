package com.meridian.backend;

import com.meridian.backend.dto.RecurringOrderRequest;
import com.meridian.backend.dto.RecurringOrderResponse;
import com.meridian.backend.model.Holding;
import com.meridian.backend.model.Order;
import com.meridian.backend.model.RecurringFrequency;
import com.meridian.backend.model.SupportedCurrency;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.User;
import com.meridian.backend.model.RecurringOrder;
import com.meridian.backend.repository.RecurringOrderRepository;
import com.meridian.backend.service.RecurringOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Recurring buys that pay from a EUR wallet. 1 EUR = 1.10 USD (1.0945 after the 0.5% spread). */
class RecurringOrderCurrencyTest extends IntegrationTestBase {

    @Autowired RecurringOrderService recurringOrderService;
    @Autowired RecurringOrderRepository recurringOrderRepository;

    private RecurringOrder storedRecurringOrder(User user) {
        return recurringOrderRepository.findByPortfolioIdOrderByCreatedAtDesc(portfolioOf(user).getId()).get(0);
    }

    @BeforeEach
    void eurIsWorthOnePointOneDollars() {
        setFxRate(SupportedCurrency.EUR, "1.1000");
    }

    @Test
    void aEuroAmountBuysTheSharesItIsWorthAndPaysFromTheEuroWallet() {
        User user = newUser("0.00");
        walletService.deposit(SupportedCurrency.EUR, new BigDecimal("500.00"), user);
        Ticker ticker = newTicker("100.00");
        RecurringOrderResponse created = recurringOrderService.create(new RecurringOrderRequest(
                ticker.getSymbol(), new BigDecimal("100.00"), RecurringFrequency.WEEKLY, SupportedCurrency.EUR), user);
        assertThat(created.settlementCurrency()).isEqualTo(SupportedCurrency.EUR);

        recurringOrderService.runDue();

        // EUR 100 is worth $110 = 1.1 shares at $100. Cost $110 + $1.00 commission = $111;
        // 111 / 1.0945 = 101.4161... rounded UP to 101.4162 EUR.
        Holding holding = holdingRepository.findByPortfolioId(portfolioOf(user).getId()).get(0);
        assertThat(holding.getQuantity()).isEqualByComparingTo("1.1");
        List<Order> orders = orderRepository.findByPortfolioIdOrderByCreatedAtDesc(portfolioOf(user).getId());
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getSettlementCurrency()).isEqualTo(SupportedCurrency.EUR);
        assertThat(orders.get(0).getSettlementAmount()).isEqualByComparingTo("101.4162");
        assertThat(walletService.getWallets(user).stream().filter(w -> w.currency() == SupportedCurrency.EUR)
                .findFirst().orElseThrow().balance()).isEqualByComparingTo("398.5838");
        assertThat(portfolioOf(user).getCashBalance()).isEqualByComparingTo("0.00");
        assertThat(storedRecurringOrder(user).getNextRunAt()).isAfter(created.nextRunAt());
    }

    @Test
    void recurringBuysWithoutACurrencyStillUseUsd() {
        User user = newUser("1000.00");
        Ticker ticker = newTicker("100.00");
        RecurringOrderResponse created = recurringOrderService.create(
                new RecurringOrderRequest(ticker.getSymbol(), new BigDecimal("200.00"), RecurringFrequency.DAILY), user);
        assertThat(created.settlementCurrency()).isEqualTo(SupportedCurrency.USD);

        recurringOrderService.runDue();

        assertThat(holdingRepository.findByPortfolioId(portfolioOf(user).getId()).get(0).getQuantity()).isEqualByComparingTo("2");
        assertThat(portfolioOf(user).getCashBalance()).isEqualByComparingTo("799.00"); // 1000 - 200 - 1.00
    }

    @Test
    void anUnaffordableEuroBuyChangesNothingAndIsNotAdvanced() {
        User user = newUser("1000.00"); // plenty of USD, but the order pays from EUR and the wallet is empty
        Ticker ticker = newTicker("100.00");
        RecurringOrderResponse created = recurringOrderService.create(new RecurringOrderRequest(
                ticker.getSymbol(), new BigDecimal("100.00"), RecurringFrequency.DAILY, SupportedCurrency.EUR), user);

        recurringOrderService.runDue();

        assertThat(holdingRepository.findByPortfolioId(portfolioOf(user).getId())).isEmpty();
        assertThat(portfolioOf(user).getCashBalance()).isEqualByComparingTo("1000.00");
        assertThat(storedRecurringOrder(user).getNextRunAt()).isEqualTo(created.nextRunAt());
    }
}
