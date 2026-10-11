package com.meridian.backend.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/** Thins a long series so it fits in a chart. */
public final class Downsample {

    private Downsample() {
    }

    /** At most {@code maxPoints} elements, picked evenly by position, always including the first and the last. */
    public static <T> List<T> evenly(List<T> rows, int maxPoints) {
        int n = rows.size();
        if (n <= maxPoints) return rows;
        List<T> picked = new ArrayList<>(maxPoints);
        for (int i = 0; i < maxPoints; i++) {
            picked.add(rows.get((int) Math.round((double) i * (n - 1) / (maxPoints - 1))));
        }
        return picked;
    }

    /**
     * At most {@code maxPoints} elements spread evenly over TIME: for each of {@code maxPoints} evenly spaced
     * moments, the latest row at or before it (a row picked twice is kept once). Picking by position let a dense
     * stretch (prices or snapshots recorded every minute lately) crowd out months of sparser history. Keeps the
     * first and the last row and the order the rows came in (newest-first or oldest-first).
     */
    public static <T> List<T> evenlyOverTime(List<T> rows, int maxPoints, Function<T, Instant> time) {
        int n = rows.size();
        if (n <= maxPoints) return rows;
        boolean newestFirst = time.apply(rows.get(0)).isAfter(time.apply(rows.get(n - 1)));
        List<T> asc = newestFirst ? reversed(rows) : rows;
        long start = time.apply(asc.get(0)).toEpochMilli();
        long end = time.apply(asc.get(n - 1)).toEpochMilli();
        if (start == end) return evenly(rows, maxPoints);

        List<T> picked = new ArrayList<>(maxPoints);
        int j = 0;
        int lastPicked = -1;
        for (int k = 0; k < maxPoints; k++) {
            long target = k == maxPoints - 1 ? end : start + Math.round((double) (end - start) * k / (maxPoints - 1));
            while (j + 1 < n && time.apply(asc.get(j + 1)).toEpochMilli() <= target) j++;
            if (j != lastPicked) {
                picked.add(asc.get(j));
                lastPicked = j;
            }
        }
        return newestFirst ? reversed(picked) : picked;
    }

    private static <T> List<T> reversed(List<T> rows) {
        List<T> copy = new ArrayList<>(rows);
        Collections.reverse(copy);
        return copy;
    }
}
