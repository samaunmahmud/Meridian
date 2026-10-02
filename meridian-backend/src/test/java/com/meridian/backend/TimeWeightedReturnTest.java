package com.meridian.backend;

import com.meridian.backend.service.TimeWeightedReturn;
import com.meridian.backend.service.TimeWeightedReturn.Flow;
import com.meridian.backend.service.TimeWeightedReturn.Valuation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TimeWeightedReturnTest {

    private static final Instant T0 = Instant.parse("2026-09-01T12:00:00Z");

    private static Valuation v(int minute, String value) {
        return new Valuation(T0.plusSeconds(60L * minute), new BigDecimal(value));
    }

    private static Flow f(int second, String amount) {
        return new Flow(T0.plusSeconds(second), new BigDecimal(amount));
    }

    @Test
    void aDepositMovesTheValueButNotTheReturn() {
        // 1000 deposited at 90s, in the period ending at minute 2: (2100 - 1000) / 1100 is flat.
        double[] g = TimeWeightedReturn.growth(
                List.of(v(0, "1000"), v(1, "1100"), v(2, "2100"), v(3, "2310")),
                List.of(f(90, "1000")));
        assertThat(g[1]).isCloseTo(1.10, within(1e-9));
        assertThat(g[2]).isCloseTo(1.10, within(1e-9));
        assertThat(g[3]).isCloseTo(1.21, within(1e-9));
    }

    @Test
    void aFlowCountsInThePeriodThatEndsAtOrAfterIt() {
        // +10%, then 1000 deposited exactly at minute 2 and another +10% on the larger balance.
        double[] g = TimeWeightedReturn.growth(
                List.of(v(0, "1000"), v(1, "1100"), v(2, "2100"), v(3, "2310")),
                List.of(f(120, "1000")));
        assertThat(g[2]).isCloseTo(1.10, within(1e-9));
        assertThat(g[3]).isCloseTo(1.21, within(1e-9));
    }

    @Test
    void withdrawalsAndFlowsBeforeTheFirstValuationAreHandled() {
        double[] g = TimeWeightedReturn.growth(
                List.of(v(0, "1000"), v(1, "600")),
                List.of(f(-30, "1000"), f(30, "-500"))); // the first deposit is already in the 1000
        assertThat(g[0]).isEqualTo(1.0);
        assertThat(g[1]).isCloseTo(1.10, within(1e-9)); // (600 + 500) / 1000
    }

    @Test
    void anEmptyAccountBeingFundedHasNoReturn() {
        double[] g = TimeWeightedReturn.growth(
                List.of(v(0, "0"), v(1, "500"), v(2, "550")),
                List.of(f(30, "500")));
        assertThat(g[1]).isEqualTo(1.0);
        assertThat(g[2]).isCloseTo(1.10, within(1e-9));
    }
}
