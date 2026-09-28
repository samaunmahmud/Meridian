package com.meridian.backend.service;

import com.meridian.backend.dto.HoldingResponse;
import com.meridian.backend.dto.PerformanceResponse;
import com.meridian.backend.dto.PerformanceResponse.SymbolPerformance;
import com.meridian.backend.dto.PerformanceResponse.Trade;
import com.meridian.backend.dto.PortfolioResponse;
import com.meridian.backend.model.Order;
import com.meridian.backend.model.OrderStatus;
import com.meridian.backend.model.User;
import com.meridian.backend.repository.OrderRepository;
import com.meridian.backend.repository.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PerformanceService {

    private final PortfolioService portfolioService;
    private final PortfolioRepository portfolioRepository;
    private final OrderRepository orderRepository;

    public PerformanceService(PortfolioService portfolioService, PortfolioRepository portfolioRepository,
                              OrderRepository orderRepository) {
        this.portfolioService = portfolioService;
        this.portfolioRepository = portfolioRepository;
        this.orderRepository = orderRepository;
    }

    // Symbol accumulator; name is filled from the ticker.
    private static final class Totals {
        String name;
        BigDecimal realized = BigDecimal.ZERO;
        BigDecimal unrealized = BigDecimal.ZERO;
        BigDecimal fees = BigDecimal.ZERO;
        int trades;
    }

    @Transactional
    public PerformanceResponse getPerformance(User user) {
        PortfolioResponse valuation = portfolioService.getPortfolioValuation(user); // also creates a missing portfolio
        Long portfolioId = portfolioRepository.findByUserId(user.getId()).orElseThrow().getId();
        List<Order> filled = orderRepository.findByPortfolioIdAndStatusOrderByCreatedAtDesc(portfolioId, OrderStatus.FILLED);
        Map<Long, BigDecimal> realizedByOrder = PnlReplay.realizedByOrder(filled);

        Map<String, Totals> bySymbol = new LinkedHashMap<>();
        BigDecimal fees = BigDecimal.ZERO;
        Trade best = null;
        Trade worst = null;
        int wins = 0;
        for (Order order : filled) {
            String symbol = order.getTicker().getSymbol();
            Totals t = bySymbol.computeIfAbsent(symbol, s -> new Totals());
            t.name = order.getTicker().getName();
            t.trades++;
            if (order.getFeeAmount() != null) {
                t.fees = t.fees.add(order.getFeeAmount());
                fees = fees.add(order.getFeeAmount());
            }
            BigDecimal pnl = realizedByOrder.get(order.getId());
            if (pnl == null) continue;
            t.realized = t.realized.add(pnl);
            if (pnl.signum() > 0) wins++;
            Trade trade = new Trade(order.getId(), symbol, order.getQuantity(), scale(pnl), order.getExecutedAt());
            if (best == null || pnl.compareTo(best.realizedPnL()) > 0) best = trade;
            if (worst == null || pnl.compareTo(worst.realizedPnL()) < 0) worst = trade;
        }
        for (HoldingResponse h : valuation.holdings()) {
            Totals t = bySymbol.computeIfAbsent(h.symbol(), s -> new Totals());
            t.name = h.name();
            t.unrealized = h.gainLoss();
        }

        BigDecimal realized = bySymbol.values().stream().map(t -> t.realized).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unrealized = bySymbol.values().stream().map(t -> t.unrealized).reduce(BigDecimal.ZERO, BigDecimal::add);
        int closed = realizedByOrder.size();
        BigDecimal winRate = closed == 0 ? null
                : BigDecimal.valueOf(wins).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(closed), 1, RoundingMode.HALF_UP);

        List<SymbolPerformance> symbols = bySymbol.entrySet().stream()
                .map(e -> {
                    Totals t = e.getValue();
                    return new SymbolPerformance(e.getKey(), t.name, scale(t.realized), scale(t.unrealized),
                            scale(t.realized.add(t.unrealized)), scale(t.fees), t.trades);
                })
                .sorted(Comparator.comparing((SymbolPerformance s) -> s.totalPnL().abs()).reversed())
                .toList();

        return new PerformanceResponse(scale(realized), scale(unrealized), scale(realized.add(unrealized)), scale(fees),
                filled.size(), closed, wins, winRate, best, worst, symbols);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
