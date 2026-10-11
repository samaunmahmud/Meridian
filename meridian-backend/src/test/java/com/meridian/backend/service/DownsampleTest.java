package com.meridian.backend.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DownsampleTest {

    private static final Instant T0 = Instant.parse("2026-05-01T00:00:00Z");

    // 100 daily points, then a week of points every minute: the shape a long-running account has.
    private static List<Instant> dailyThenDense() {
        List<Instant> rows = new ArrayList<>();
        for (int d = 0; d < 100; d++) rows.add(T0.plus(Duration.ofDays(d)));
        Instant dense = T0.plus(Duration.ofDays(100));
        for (int m = 0; m < 7 * 24 * 60; m++) rows.add(dense.plus(Duration.ofMinutes(m)));
        return rows;
    }

    @Test
    void spreadsPointsOverTimeSoADenseRecentStretchDoesNotCrowdOutHistory() {
        List<Instant> rows = dailyThenDense();

        List<Instant> picked = Downsample.evenlyOverTime(rows, 200, t -> t);

        long older = picked.stream().filter(t -> t.isBefore(T0.plus(Duration.ofDays(100)))).count();
        assertThat(older).as("the 100 days before the dense week keep most of the points").isGreaterThan(90);
        assertThat(picked).hasSizeLessThanOrEqualTo(200).isSorted();
        assertThat(picked.get(0)).isEqualTo(rows.get(0));
        assertThat(picked.get(picked.size() - 1)).isEqualTo(rows.get(rows.size() - 1));
        // by position, the dense week would have taken nearly all of them
        long olderByPosition = Downsample.evenly(rows, 200).stream().filter(t -> t.isBefore(T0.plus(Duration.ofDays(100)))).count();
        assertThat(olderByPosition).isLessThan(10);
    }

    @Test
    void keepsNewestFirstOrder() {
        List<Instant> rows = dailyThenDense();
        Collections.reverse(rows);

        List<Instant> picked = Downsample.evenlyOverTime(rows, 50, t -> t);

        assertThat(picked.get(0)).isEqualTo(rows.get(0));
        assertThat(picked.get(picked.size() - 1)).isEqualTo(rows.get(rows.size() - 1));
        assertThat(picked).isSortedAccordingTo(Collections.reverseOrder());
    }

    @Test
    void leavesShortSeriesAlone() {
        List<Instant> rows = List.of(T0, T0.plusSeconds(1), T0.plusSeconds(2));
        assertThat(Downsample.evenlyOverTime(rows, 10, t -> t)).isSameAs(rows);
    }
}
