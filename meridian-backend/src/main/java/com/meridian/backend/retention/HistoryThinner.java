package com.meridian.backend.retention;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Thins an ever-growing history (prices, portfolio snapshots) that is written every few seconds or minutes.
 * The last {@code fullResolution} is left alone. Older rows are reduced to the NEWEST row in each hour (the
 * hour's closing value), and rows older than {@code dailyAfter} to the newest in each day, separately for
 * every group (each ticker, each portfolio). Each group is read in time order through its
 * (group, recorded_at) index, so the result does not depend on row ids, and running it again changes nothing.
 */
public final class HistoryThinner {

    public record Row(long id, Instant at) {
    }

    /** Rows of one group at or before {@code upTo}, after ({@code afterAt}, {@code afterId}) in (time, id) order. */
    public interface PageSource {
        List<Row> page(long group, Instant afterAt, long afterId, Instant upTo, int limit);
    }

    private record Bucket(long size, long index) {
    }

    private record Kept(Bucket bucket, long id) {
    }

    private static final long HOUR_MS = Duration.ofHours(1).toMillis();
    private static final long DAY_MS = Duration.ofDays(1).toMillis();

    private HistoryThinner() {
    }

    /**
     * @param groups the groups to thin (tickers, portfolios)
     * @param delete removes the rows with these ids
     * @return how many rows were removed
     */
    public static int thin(List<Long> groups, PageSource source, Consumer<List<Long>> delete, Instant now,
                           Duration fullResolution, Duration dailyAfter, int pageSize, int deleteBatch) {
        Instant cutoff = now.minus(fullResolution);
        int removed = 0;
        for (long group : groups) {
            List<Long> doomed = new ArrayList<>();
            Kept newest = null;
            Instant afterAt = Instant.EPOCH;
            long afterId = 0;

            while (true) {
                List<Row> page = source.page(group, afterAt, afterId, cutoff, pageSize);
                for (Row row : page) {
                    afterAt = row.at();
                    afterId = row.id();
                    long size = Duration.between(row.at(), now).compareTo(dailyAfter) > 0 ? DAY_MS : HOUR_MS;
                    Bucket bucket = new Bucket(size, Math.floorDiv(row.at().toEpochMilli(), size));
                    if (newest != null && newest.bucket().equals(bucket)) {
                        doomed.add(newest.id()); // a newer row of the same hour/day replaces it
                    }
                    newest = new Kept(bucket, row.id());
                    if (doomed.size() >= deleteBatch) {
                        removed += doomed.size();
                        delete.accept(List.copyOf(doomed));
                        doomed.clear();
                    }
                }
                if (page.size() < pageSize) break;
            }
            if (!doomed.isEmpty()) {
                removed += doomed.size();
                delete.accept(List.copyOf(doomed));
            }
        }
        return removed;
    }
}
