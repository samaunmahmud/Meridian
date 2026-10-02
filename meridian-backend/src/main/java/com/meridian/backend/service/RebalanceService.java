package com.meridian.backend.service;

import com.meridian.backend.dto.HoldingResponse;
import com.meridian.backend.dto.PortfolioResponse;
import com.meridian.backend.dto.RebalanceResponse;
import com.meridian.backend.dto.RebalanceResponse.Cash;
import com.meridian.backend.dto.RebalanceResponse.Row;
import com.meridian.backend.dto.RebalanceResponse.Trade;
import com.meridian.backend.dto.TargetsRequest;
import com.meridian.backend.exception.InvalidRequestException;
import com.meridian.backend.model.AllocationTarget;
import com.meridian.backend.model.OrderType;
import com.meridian.backend.model.Portfolio;
import com.meridian.backend.model.PriceHistory;
import com.meridian.backend.model.Ticker;
import com.meridian.backend.model.User;
import com.meridian.backend.repository.AllocationTargetRepository;
import com.meridian.backend.repository.PortfolioRepository;
import com.meridian.backend.repository.PriceHistoryRepository;
import com.meridian.backend.repository.TickerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Target allocation: what share of the portfolio each stock should be, how far it has drifted, and the trades
 * that would bring it back. Works on the USD total the rest of the app reports (USD cash plus holdings).
 * Only suggests trades; nothing is bought or sold until the person places the order.
 */
@Service
public class RebalanceService {

    /** Rows this close to their target (in percentage points) are left alone. */
    public static final BigDecimal TOLERANCE_BAND = new BigDecimal("1.00");
    /** Smaller trades are not worth the $1 minimum commission. */
    public static final BigDecimal MIN_TRADE = new BigDecimal("5.00");
    public static final int MAX_TARGETS = 50;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PortfolioService portfolioService;
    private final PortfolioRepository portfolioRepository;
    private final AllocationTargetRepository targetRepository;
    private final TickerRepository tickerRepository;
    private final PriceHistoryRepository priceHistoryRepository;
    private final FeeService feeService;

    public RebalanceService(PortfolioService portfolioService, PortfolioRepository portfolioRepository,
                            AllocationTargetRepository targetRepository, TickerRepository tickerRepository,
                            PriceHistoryRepository priceHistoryRepository, FeeService feeService) {
        this.portfolioService = portfolioService;
        this.portfolioRepository = portfolioRepository;
        this.targetRepository = targetRepository;
        this.tickerRepository = tickerRepository;
        this.priceHistoryRepository = priceHistoryRepository;
        this.feeService = feeService;
    }

    private static final class Line {
        String symbol;
        String name;
        BigDecimal price;
        BigDecimal shares = BigDecimal.ZERO;
        BigDecimal sellable = BigDecimal.ZERO;
        BigDecimal value = BigDecimal.ZERO;
        BigDecimal target = BigDecimal.ZERO;
    }

    @Transactional
    public RebalanceResponse getPlan(User user) {
        PortfolioResponse valuation = portfolioService.getPortfolioValuation(user); // also creates a missing portfolio
        Long portfolioId = portfolioRepository.findByUserId(user.getId()).orElseThrow().getId();
        List<AllocationTarget> targets = targetRepository.findByPortfolioId(portfolioId);

        // Holdings, biggest first, then stocks with a target that aren't held yet.
        Map<String, Line> lines = new LinkedHashMap<>();
        valuation.holdings().stream()
                .sorted((a, b) -> b.marketValue().compareTo(a.marketValue()))
                .forEach(h -> lines.put(h.symbol(), fromHolding(h)));
        for (AllocationTarget t : targets) {
            Line line = lines.computeIfAbsent(t.getTicker().getSymbol(), s -> fromTicker(t.getTicker()));
            line.target = t.getTargetPercent();
        }

        BigDecimal total = valuation.totalValue();
        BigDecimal targetSum = targets.stream().map(AllocationTarget::getTargetPercent).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean hasTargets = !targets.isEmpty();

        List<Row> rows = new ArrayList<>();
        for (Line line : lines.values()) {
            BigDecimal current = percentOf(line.value, total);
            Trade trade = hasTargets ? tradeFor(line, current, total) : null;
            rows.add(new Row(line.symbol, line.name, line.price, line.shares, money(line.value), current,
                    line.target.setScale(2, RoundingMode.HALF_UP), trade));
        }
        Cash cash = new Cash(money(valuation.cashBalance()), percentOf(valuation.cashBalance(), total),
                HUNDRED.subtract(targetSum).setScale(2, RoundingMode.HALF_UP));
        return new RebalanceResponse(hasTargets, money(total), TOLERANCE_BAND, cash, rows);
    }

    private Line fromHolding(HoldingResponse h) {
        Line line = new Line();
        line.symbol = h.symbol();
        line.name = h.name();
        line.price = h.currentPrice();
        line.shares = h.quantity();
        line.sellable = h.availableQuantity();
        line.value = h.marketValue();
        return line;
    }

    private Line fromTicker(Ticker ticker) {
        Line line = new Line();
        line.symbol = ticker.getSymbol();
        line.name = ticker.getName();
        line.price = priceHistoryRepository.findFirstByTickerIdOrderByRecordedAtDesc(ticker.getId())
                .map(PriceHistory::getPrice).orElse(null);
        return line;
    }

    private Trade tradeFor(Line line, BigDecimal currentPercent, BigDecimal total) {
        if (line.price == null || line.price.signum() <= 0 || total.signum() <= 0) return null;
        if (currentPercent.subtract(line.target).abs().compareTo(TOLERANCE_BAND) < 0) return null;
        BigDecimal targetValue = total.multiply(line.target).divide(HUNDRED, 4, RoundingMode.HALF_UP);
        BigDecimal gap = targetValue.subtract(line.value);

        OrderType type;
        BigDecimal quantity;
        if (gap.signum() < 0) {
            type = OrderType.SELL;
            // A target of 0 means all of it; shares held for open sell orders can't be sold again.
            quantity = line.target.signum() == 0 ? line.shares
                    : gap.negate().divide(line.price, 4, RoundingMode.DOWN);
            quantity = quantity.min(line.sellable);
        } else {
            type = OrderType.BUY;
            BigDecimal spend = gap.subtract(feeService.commissionFor(gap));
            quantity = spend.signum() <= 0 ? BigDecimal.ZERO : spend.divide(line.price, 4, RoundingMode.DOWN);
        }
        BigDecimal estimate = money(quantity.multiply(line.price));
        if (quantity.signum() <= 0 || estimate.compareTo(MIN_TRADE) < 0) return null;
        return new Trade(type, quantity.stripTrailingZeros(), estimate);
    }

    /** Replaces all targets (a 0% target is the same as none) and returns the new plan. */
    @Transactional
    public RebalanceResponse setTargets(User user, TargetsRequest request) {
        List<TargetsRequest.Target> wanted = request == null || request.targets() == null ? List.of() : request.targets();
        if (wanted.size() > MAX_TARGETS) {
            throw new InvalidRequestException("At most " + MAX_TARGETS + " targets");
        }
        portfolioService.getPortfolioValuation(user); // creates a missing portfolio
        Portfolio portfolio = portfolioRepository.findByUserIdForUpdate(user.getId()).orElseThrow();

        Set<String> seen = new HashSet<>();
        BigDecimal sum = BigDecimal.ZERO;
        List<AllocationTarget> rows = new ArrayList<>();
        for (TargetsRequest.Target t : wanted) {
            String symbol = t == null || t.symbol() == null ? "" : t.symbol().trim().toUpperCase();
            if (symbol.isEmpty()) throw new InvalidRequestException("Every target needs a stock");
            if (!seen.add(symbol)) throw new InvalidRequestException(symbol + " has more than one target");
            BigDecimal percent = t.percent();
            if (percent == null || percent.signum() < 0 || percent.compareTo(HUNDRED) > 0
                    || percent.stripTrailingZeros().scale() > 2) {
                throw new InvalidRequestException("The target for " + symbol + " must be between 0 and 100%, with at most 2 decimals");
            }
            Ticker ticker = tickerRepository.findBySymbol(symbol)
                    .orElseThrow(() -> new InvalidRequestException(symbol + " is not a tracked stock"));
            if (percent.signum() == 0) continue;
            sum = sum.add(percent);
            rows.add(new AllocationTarget(portfolio, ticker, percent));
        }
        if (sum.compareTo(HUNDRED) > 0) {
            throw new InvalidRequestException("The targets add up to " + sum.stripTrailingZeros().toPlainString()
                    + "%; they can be at most 100% (the rest is cash)");
        }
        targetRepository.deleteByPortfolioId(portfolio.getId());
        targetRepository.flush();
        targetRepository.saveAll(rows);
        return getPlan(user);
    }

    private static BigDecimal percentOf(BigDecimal part, BigDecimal total) {
        if (total.signum() <= 0) return BigDecimal.ZERO.setScale(2);
        return part.multiply(HUNDRED).divide(total, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
