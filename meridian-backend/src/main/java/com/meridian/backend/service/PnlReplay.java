package com.meridian.backend.service;

import com.meridian.backend.model.Order;
import com.meridian.backend.model.OrderStatus;
import com.meridian.backend.model.OrderType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Works out the realized gain or loss of every past sell. Only the current
// average cost is stored on a holding, so this replays the filled orders in
// the order they executed, updating the average exactly as a buy does
// (price * quantity, fees excluded, 4 decimal places) and pricing each sell
// against the average at that moment.
final class PnlReplay {

    private PnlReplay() {
    }

    private static final class Position {
        BigDecimal quantity = BigDecimal.ZERO;
        BigDecimal avgCost = BigDecimal.ZERO;
    }

    /**
     * Realized P&L in USD by order id, for sells only. A sell with no earlier
     * buy on record (data older than order history) is left out: its cost is unknown.
     */
    static Map<Long, BigDecimal> realizedByOrder(List<Order> orders) {
        List<Order> filled = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.FILLED && o.getPrice() != null)
                .sorted(Comparator.comparing((Order o) -> o.getExecutedAt() != null ? o.getExecutedAt() : o.getCreatedAt())
                        .thenComparing(Order::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        Map<Long, Position> positions = new HashMap<>();
        Map<Long, BigDecimal> realized = new HashMap<>();
        for (Order order : filled) {
            Position p = positions.computeIfAbsent(order.getTicker().getId(), id -> new Position());
            BigDecimal quantity = order.getQuantity();
            if (order.getType() == OrderType.BUY) {
                BigDecimal newQuantity = p.quantity.add(quantity);
                p.avgCost = p.quantity.signum() == 0
                        ? order.getPrice()
                        : p.avgCost.multiply(p.quantity).add(order.getPrice().multiply(quantity))
                                .divide(newQuantity, 4, RoundingMode.HALF_UP);
                p.quantity = newQuantity;
            } else {
                if (p.quantity.compareTo(quantity) >= 0) {
                    realized.put(order.getId(), order.getPrice().subtract(p.avgCost).multiply(quantity));
                }
                p.quantity = p.quantity.subtract(quantity).max(BigDecimal.ZERO);
            }
        }
        return realized;
    }
}
