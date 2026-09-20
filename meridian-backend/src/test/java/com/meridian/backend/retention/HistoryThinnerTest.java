package com.meridian.backend.retention;

import com.meridian.backend.retention.HistoryThinner.Row;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryThinnerTest {

    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
    private static final Duration FULL = Duration.ofDays(7);
    private static final Duration DAILY_AFTER = Duration.ofDays(90);

    private record Stored(long id, long group, Instant at) {
    }

    /** A fake table. Rows can be added in any id order; `thin` reads and deletes through the same interface as the database. */
    private static final class Table {
        final List<Stored> rows = new ArrayList<>();
        long nextId = 1;

        void add(long group, Instant at) {
            rows.add(new Stored(nextId++, group, at));
        }

        List<Row> page(long group, Instant afterAt, long afterId, Instant upTo, int limit) {
            return rows.stream()
                    .filter(r -> r.group() == group && !r.at().isAfter(upTo)
                            && (r.at().isAfter(afterAt) || (r.at().equals(afterAt) && r.id() > afterId)))
                    .sorted(Comparator.comparing(Stored::at).thenComparing(Stored::id))
                    .limit(limit).map(r -> new Row(r.id(), r.at())).toList();
        }

        int thin() {
            return thinAt(NOW);
        }

        int thinAt(Instant now) {
            List<Long> groups = rows.stream().map(Stored::group).distinct().toList();
            return HistoryThinner.thin(groups, this::page, ids -> rows.removeIf(r -> ids.contains(r.id())), now, FULL, DAILY_AFTER, 30, 20);
        }

        List<Stored> of(long group) {
            return rows.stream().filter(r -> r.group() == group).toList();
        }

        Set<Long> ids() {
            return rows.stream().map(Stored::id).collect(Collectors.toCollection(TreeSet::new));
        }
    }

    /** One row every 10 minutes, oldest first, for `days` days ending `endedDaysAgo` days ago. */
    private static void tenMinuteRows(Table t, long group, int days, int endedDaysAgo) {
        Instant end = NOW.minus(Duration.ofDays(endedDaysAgo));
        for (Instant at = end.minus(Duration.ofDays(days)); at.isBefore(end); at = at.plus(Duration.ofMinutes(10))) {
            t.add(group, at);
        }
    }

    private static int minuteOf(Stored r) {
        return r.at().atZone(ZoneOffset.UTC).getMinute();
    }

    @Test
    void recentRowsAreNeverTouched() {
        Table t = new Table();
        tenMinuteRows(t, 1, 7, 0);
        int before = t.rows.size();

        assertThat(t.thin()).isZero();
        assertThat(t.rows).hasSize(before);
    }

    @Test
    void rowsOlderThanAWeekKeepOnlyTheNewestOfEachHour() {
        Table t = new Table();
        tenMinuteRows(t, 1, 2, 10); // two days, ten days ago: 6 rows per hour

        t.thin();

        assertThat(t.rows).hasSize(48);
        assertThat(t.rows).allSatisfy(r -> assertThat(minuteOf(r)).as("the hour's LAST row (minute 50) survives").isEqualTo(50));
    }

    @Test
    void rowsOlderThanNinetyDaysKeepOnlyTheNewestOfEachDay() {
        Table t = new Table();
        Instant midnight = NOW.truncatedTo(ChronoUnit.DAYS).minus(Duration.ofDays(203)); // whole days
        for (Instant at = midnight; at.isBefore(midnight.plus(Duration.ofDays(3))); at = at.plus(Duration.ofMinutes(10))) {
            t.add(1, at);
        }

        t.thin();

        assertThat(t.rows).hasSize(3);
        assertThat(t.rows).allSatisfy(r -> assertThat(r.at().atZone(ZoneOffset.UTC).getHour()).isEqualTo(23));
    }

    @Test
    void everyGroupIsThinnedOnItsOwnEvenWhenTheirRowsAreInterleaved() {
        Table t = new Table();
        Instant start = NOW.minus(Duration.ofDays(10));
        for (int i = 0; i < 6 * 24; i++) {
            t.add(1, start.plus(Duration.ofMinutes(10L * i)));
            t.add(2, start.plus(Duration.ofMinutes(10L * i + 5)));
        }

        t.thin();

        assertThat(t.of(1)).hasSize(24);
        assertThat(t.of(2)).hasSize(24);
    }

    @Test
    void rowIdsDoNotHaveToGrowWithTime() {
        // A backfill or an import can add OLD rows after new ones (or, as in a SQL generator, newest first).
        Table t = new Table();
        Instant start = NOW.minus(Duration.ofDays(12));
        List<Instant> times = new ArrayList<>();
        for (int i = 0; i < 6 * 24 * 2; i++) times.add(start.plus(Duration.ofMinutes(10L * i)));
        java.util.Collections.reverse(times);          // ids run from the newest row to the oldest
        times.forEach(at -> t.add(1, at));
        for (int i = 0; i < 5; i++) t.add(1, NOW.minusSeconds(60L * i)); // and recent rows come last

        t.thin();

        assertThat(t.rows.stream().filter(r -> r.at().isBefore(NOW.minus(FULL)))).hasSize(48);
        assertThat(t.rows.stream().filter(r -> !r.at().isBefore(NOW.minus(FULL)))).hasSize(5);
    }

    @Test
    void theNewestRowOfAGroupIsAlwaysKeptEvenIfItIsAncient() {
        Table t = new Table();
        t.add(1, NOW.minus(Duration.ofDays(400)));
        t.add(1, NOW.minus(Duration.ofDays(400)).plusSeconds(20));
        t.add(2, NOW.minusSeconds(5));

        t.thin();

        assertThat(t.of(1)).singleElement().satisfies(r -> assertThat(r.id()).isEqualTo(2)); // the newer of the two
        assertThat(t.of(2)).hasSize(1);
    }

    @Test
    void runningItAgainChangesNothing() {
        Table t = new Table();
        tenMinuteRows(t, 1, 30, 8);
        tenMinuteRows(t, 1, 5, 0);
        t.thin();
        Set<Long> afterFirst = t.ids();

        int second = t.thin();

        assertThat(second).isZero();
        assertThat(t.ids()).isEqualTo(afterFirst);
    }

    @Test
    void anHourThatStraddlesTheCutoffIsThinnedOnceItsRowsAreAllOldEnough() {
        Table t = new Table();
        Instant hourStart = NOW.minus(FULL).truncatedTo(ChronoUnit.HOURS);
        for (int m = 0; m < 60; m += 10) t.add(1, hourStart.plus(Duration.ofMinutes(m)));

        t.thin(); // part of this hour is still inside the full-resolution week
        assertThat(t.rows).isNotEmpty();

        t.thinAt(NOW.plus(Duration.ofDays(1))); // a day later the whole hour is old enough
        assertThat(t.rows).hasSize(1);
    }

    @Test
    void deletesInBatchesSoNothingIsHeldInMemoryOrOneHugeStatement() {
        Table t = new Table();
        tenMinuteRows(t, 1, 5, 10);
        List<Integer> batchSizes = new ArrayList<>();

        HistoryThinner.thin(List.of(1L), t::page, ids -> {
            batchSizes.add(ids.size());
            t.rows.removeIf(r -> ids.contains(r.id()));
        }, NOW, FULL, DAILY_AFTER, 30, 20);

        assertThat(batchSizes).hasSizeGreaterThan(1).allSatisfy(n -> assertThat(n).isLessThanOrEqualTo(20));
    }

    @Test
    void readsLongHistoriesPageByPage() {
        Table t = new Table();
        tenMinuteRows(t, 1, 40, 10); // 5 760 rows, far more than one 30-row page
        int before = t.rows.size();

        int removed = t.thin();

        assertThat(t.rows.size() + removed).isEqualTo(before);
        assertThat(t.rows).hasSize(40 * 24);
    }
}
