package com.meridian.backend.service;

import java.util.ArrayList;
import java.util.List;

/** Picks evenly spaced elements (by position) so a long series fits in a chart. */
public final class Downsample {

    private Downsample() {
    }

    /** At most {@code maxPoints} elements, always including the first and the last. */
    public static <T> List<T> evenly(List<T> rows, int maxPoints) {
        int n = rows.size();
        if (n <= maxPoints) return rows;
        List<T> picked = new ArrayList<>(maxPoints);
        for (int i = 0; i < maxPoints; i++) {
            picked.add(rows.get((int) Math.round((double) i * (n - 1) / (maxPoints - 1))));
        }
        return picked;
    }
}
