package com.meridian.backend.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Time-weighted return over a series of valuations: each period's growth is (value - money added) / previous value,
 * and the periods are chained, so adding or taking out money moves the value but not the return.
 */
public final class TimeWeightedReturn {

    private TimeWeightedReturn() {
    }

    public record Valuation(Instant at, BigDecimal value) {
    }

    /** Money put in (positive) or taken out (negative) at a moment. */
    public record Flow(Instant at, BigDecimal amount) {
    }

    /**
     * Growth of 1 at each valuation (the first is 1). A flow counts in the period that ends at the first valuation
     * at or after it. A period that starts at zero or less has no return (an empty account being funded).
     *
     * @param valuations oldest first
     * @param flows      in any order
     */
    public static double[] growth(List<Valuation> valuations, List<Flow> flows) {
        List<Flow> sorted = flows.stream().sorted((a, b) -> a.at().compareTo(b.at())).toList();
        double[] growth = new double[valuations.size()];
        int f = 0;
        // Flows before the first valuation are already in it.
        while (f < sorted.size() && !valuations.isEmpty() && !sorted.get(f).at().isAfter(valuations.get(0).at())) f++;
        double index = 1;
        for (int i = 0; i < valuations.size(); i++) {
            if (i > 0) {
                double added = 0;
                while (f < sorted.size() && !sorted.get(f).at().isAfter(valuations.get(i).at())) {
                    added += sorted.get(f++).amount().doubleValue();
                }
                double previous = valuations.get(i - 1).value().doubleValue();
                if (previous > 0) {
                    index *= (valuations.get(i).value().doubleValue() - added) / previous;
                }
            }
            growth[i] = index;
        }
        return growth;
    }
}
