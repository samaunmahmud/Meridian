package com.meridian.backend;

import com.meridian.backend.dto.OrderRequest;
import com.meridian.backend.dto.RebalanceResponse;
import com.meridian.backend.dto.RebalanceResponse.Row;
import com.meridian.backend.dto.TargetsRequest;
import com.meridian.backend.dto.TargetsRequest.Target;
import com.meridian.backend.exception.InvalidRequestException;
import com.meridian.backend.model.OrderKind;
import com.meridian.backend.model.OrderType;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.User;
import com.meridian.backend.service.PortfolioService;
import com.meridian.backend.service.RebalanceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RebalanceTest extends IntegrationTestBase {

    @Autowired RebalanceService rebalanceService;
    @Autowired PortfolioService portfolioService;

    private void buy(User user, Ticker t, String qty) {
        portfolioService.placeOrder(new OrderRequest(t.getSymbol(), OrderType.BUY, OrderKind.MARKET, new BigDecimal(qty), null, null), user);
    }

    private RebalanceResponse set(User user, Object... symbolPercentPairs) {
        List<Target> targets = new java.util.ArrayList<>();
        for (int i = 0; i < symbolPercentPairs.length; i += 2) {
            targets.add(new Target((String) symbolPercentPairs[i], new BigDecimal((String) symbolPercentPairs[i + 1])));
        }
        return rebalanceService.setTargets(user, new TargetsRequest(targets));
    }

    private static Row row(RebalanceResponse plan, Ticker t) {
        return plan.rows().stream().filter(r -> r.symbol().equals(t.getSymbol())).findFirst().orElseThrow();
    }

    @Test
    void suggestsSellingWhatIsOverweightAndBuyingWhatIsUnderweightLeavingRoomForFees() {
        User user = newUser("10000.00");
        Ticker a = newTicker("100.00");
        Ticker b = newTicker("50.00");
        buy(user, a, "30"); // $3,000 + $7.50 commission: cash 6,992.50, total 9,992.50

        RebalanceResponse plan = set(user, a.getSymbol(), "20", b.getSymbol().toLowerCase(), "30");

        assertThat(plan.hasTargets()).isTrue();
        assertThat(plan.totalValue()).isEqualByComparingTo("9992.50");
        assertThat(plan.rows()).extracting(Row::symbol).containsExactly(a.getSymbol(), b.getSymbol()); // held first
        Row ra = row(plan, a);
        assertThat(ra.currentPercent()).isEqualByComparingTo("30.02");
        assertThat(ra.targetPercent()).isEqualByComparingTo("20.00");
        assertThat(ra.trade().type()).isEqualTo(OrderType.SELL);
        assertThat(ra.trade().quantity()).isEqualByComparingTo("10.015");     // 1,001.50 over / $100
        assertThat(ra.trade().estimatedValue()).isEqualByComparingTo("1001.50");
        Row rb = row(plan, b);
        assertThat(rb.shares()).isEqualByComparingTo("0");
        assertThat(rb.price()).isEqualByComparingTo("50.00");
        assertThat(rb.trade().type()).isEqualTo(OrderType.BUY);
        assertThat(rb.trade().quantity()).isEqualByComparingTo("59.8051");    // (2,997.75 - 7.4944 fee) / $50
        assertThat(rb.trade().estimatedValue()).isEqualByComparingTo("2990.26");
        assertThat(plan.cash().currentPercent()).isEqualByComparingTo("69.98");
        assertThat(plan.cash().targetPercent()).isEqualByComparingTo("50.00");
    }

    @Test
    void rowsCloseToTheirTargetAndTinyTradesAreLeftAlone() {
        User user = newUser("1000.00");
        Ticker a = newTicker("10.00");
        Ticker tiny = newTicker("10.00");
        buy(user, a, "50"); // $500 + $1.25: total 998.75, A is 50.06%

        RebalanceResponse plan = set(user, a.getSymbol(), "50", tiny.getSymbol(), "0.40"); // $3.99: under the $5 minimum

        assertThat(row(plan, a).trade()).isNull();
        assertThat(row(plan, tiny).trade()).isNull();
    }

    @Test
    void aSmallDriftIsLeftAloneEvenWhenTheTradeWouldBeLarge() {
        User user = newUser("100000.00");
        Ticker a = newTicker("100.00");
        buy(user, a, "500"); // $50,000 + $125: total 99,875, A is 50.06%

        Row close = row(set(user, a.getSymbol(), "49.5"), a);  // 0.56 points off (a $561 trade): left alone
        assertThat(close.trade()).isNull();
        Row far = row(set(user, a.getSymbol(), "48.5"), a);    // 1.56 points off: sell
        assertThat(far.trade().type()).isEqualTo(OrderType.SELL);
    }

    @Test
    void aHoldingWithoutATargetIsSoldInFullButNotSharesHeldForOpenOrders() {
        User user = newUser("10000.00");
        Ticker kept = newTicker("10.00");
        Ticker dropped = newTicker("20.00");
        buy(user, kept, "10");
        buy(user, dropped, "10");
        portfolioService.placeOrder(new OrderRequest(dropped.getSymbol(), OrderType.SELL, OrderKind.LIMIT,
                new BigDecimal("3"), new BigDecimal("999"), null), user); // 3 shares held for this order

        RebalanceResponse plan = set(user, kept.getSymbol(), "1");

        Row r = row(plan, dropped);
        assertThat(r.targetPercent()).isEqualByComparingTo("0");
        assertThat(r.trade().type()).isEqualTo(OrderType.SELL);
        assertThat(r.trade().quantity()).isEqualByComparingTo("7");
    }

    @Test
    void withoutTargetsNothingIsSuggestedAndClearingThemWorks() {
        User user = newUser("10000.00");
        Ticker a = newTicker("100.00");
        buy(user, a, "10");

        RebalanceResponse none = rebalanceService.getPlan(user);
        assertThat(none.hasTargets()).isFalse();
        assertThat(none.cash().targetPercent()).isEqualByComparingTo("100");
        assertThat(row(none, a).trade()).isNull();

        set(user, a.getSymbol(), "60");
        RebalanceResponse replaced = set(user, a.getSymbol(), "0");
        assertThat(replaced.hasTargets()).isFalse();
        assertThat(rebalanceService.setTargets(user, new TargetsRequest(null)).hasTargets()).isFalse();
    }

    @Test
    void newTargetsReplaceTheOldOnes() {
        User user = newUser("10000.00");
        Ticker a = newTicker("10.00");
        Ticker b = newTicker("10.00");
        set(user, a.getSymbol(), "40", b.getSymbol(), "40");

        RebalanceResponse plan = set(user, b.getSymbol(), "25");

        assertThat(plan.rows()).extracting(Row::symbol).containsExactly(b.getSymbol());
        assertThat(plan.cash().targetPercent()).isEqualByComparingTo("75");
    }

    @Test
    void badTargetsAreRefusedAndChangeNothing() {
        User user = newUser("10000.00");
        Ticker a = newTicker("10.00");
        Ticker b = newTicker("10.00");
        set(user, a.getSymbol(), "10");

        assertThatThrownBy(() -> set(user, a.getSymbol(), "60", b.getSymbol(), "40.01"))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("100.01%");
        assertThatThrownBy(() -> set(user, a.getSymbol(), "10", a.getSymbol().toLowerCase(), "10"))
                .isInstanceOf(InvalidRequestException.class).hasMessageContaining("more than one");
        assertThatThrownBy(() -> set(user, "NOPE999", "10")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> set(user, a.getSymbol(), "-1")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> set(user, a.getSymbol(), "10.555")).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> set(user, " ", "10")).isInstanceOf(InvalidRequestException.class);

        RebalanceResponse unchanged = rebalanceService.getPlan(user);
        assertThat(row(unchanged, a).targetPercent()).isEqualByComparingTo("10");
    }
}
