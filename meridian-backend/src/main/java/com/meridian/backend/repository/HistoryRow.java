package com.meridian.backend.repository;

import java.time.Instant;

/** Just the columns the retention job needs, so thinning millions of rows does not load whole entities. */
public interface HistoryRow {
    Long getId();

    Instant getRecordedAt();
}
