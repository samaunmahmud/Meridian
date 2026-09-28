package com.meridian.backend;

import com.meridian.backend.dto.OrderRequest;
import com.meridian.backend.dto.OrderResponse;
import com.meridian.backend.dto.PerformanceResponse;
import com.meridian.backend.dto.PerformanceResponse.SymbolPerformance;
import com.meridian.backend.model.OrderKind;
import com.meridian.backend.model.OrderType;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.User;
import com.meridian.backend.service.PerformanceService;
import com.meridian.backend.service.PortfolioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PerformanceTest extends IntegrationTestBase {

    @Autowired PortfolioService portfolioService;
    @Autowired PerformanceService performanceService;

    private OrderResponse trade(User user, Ticker t, OrderType type, String qty) {
        return portfolioService.placeOrder(
                new OrderRequest(t.getSymbol(), type, OrderKind.MARKET, new BigDecimal(qty), null, null), user);
    }

    @Test
    void realizedGainsUseTheAverageCostAtTheTimeOfEachSell() {
        User user = newUser("10000.00");
        Ticker a = newTicker("100.00");
        Ticker b = newTicker("50.00");

        trade(user, a, OrderType.BUY, "2");
        setPrice(a, "110.00");
        trade(user, a, OrderType.BUY, "2");   // average cost now 105
        setPrice(a, "120.00");
        trade(user, a, OrderType.SELL, "1");  // +15
        setPrice(a, "90.00");
        trade(user, a, OrderType.SELL, "1");  // -15, 2 shares left at 105 now worth 90: -30 unrealized
        trade(user, b, OrderType.BUY, "4");
        setPrice(b, "60.00");
        trade(user, b, OrderType.SELL, "4");  // +40

        PerformanceResponse p = performanceService.getPerformance(user);

        assertThat(p.realizedPnL()).isEqualByComparingTo("40.00");
        assertThat(p.unrealizedPnL()).isEqualByComparingTo("-30.00");
        assertThat(p.totalPnL()).isEqualByComparingTo("10.00");
        assertThat(p.feesPaid()).isEqualByComparingTo("6.00"); // six trades at the $1 minimum
        assertThat(p.filledOrders()).isEqualTo(6);
        assertThat(p.closedTrades()).isEqualTo(3);
        assertThat(p.winningTrades()).isEqualTo(2);
        assertThat(p.winRate()).isEqualByComparingTo("66.7");
        assertThat(p.bestTrade().symbol()).isEqualTo(b.getSymbol());
        assertThat(p.bestTrade().realizedPnL()).isEqualByComparingTo("40.00");
        assertThat(p.worstTrade().symbol()).isEqualTo(a.getSymbol());
        assertThat(p.worstTrade().realizedPnL()).isEqualByComparingTo("-15.00");

        SymbolPerformance first = p.bySymbol().get(0);
        assertThat(first.symbol()).isEqualTo(b.getSymbol()); // biggest move first
        assertThat(first.totalPnL()).isEqualByComparingTo("40.00");
        SymbolPerformance second = p.bySymbol().get(1);
        assertThat(second.realizedPnL()).isEqualByComparingTo("0.00");
        assertThat(second.unrealizedPnL()).isEqualByComparingTo("-30.00");
        assertThat(second.feesPaid()).isEqualByComparingTo("4.00");
        assertThat(second.trades()).isEqualTo(4);
    }

    @Test
    void orderHistoryShowsWhatEachSellRealized() {
        User user = newUser("10000.00");
        Ticker a = newTicker("100.00");
        trade(user, a, OrderType.BUY, "3");
        setPrice(a, "104.50");
        trade(user, a, OrderType.SELL, "2");

        List<OrderResponse> history = portfolioService.getOrderHistory(user);

        OrderResponse sell = history.stream().filter(o -> o.type() == OrderType.SELL).findFirst().orElseThrow();
        OrderResponse buy = history.stream().filter(o -> o.type() == OrderType.BUY).findFirst().orElseThrow();
        assertThat(sell.realizedPnL()).isEqualByComparingTo("9.00");
        assertThat(buy.realizedPnL()).isNull();
    }

    @Test
    void aNewAccountHasNothingToReport() {
        PerformanceResponse p = performanceService.getPerformance(newUser("500.00"));
        assertThat(p.realizedPnL()).isEqualByComparingTo("0");
        assertThat(p.winRate()).isNull();
        assertThat(p.bestTrade()).isNull();
        assertThat(p.bySymbol()).isEmpty();
    }
}
