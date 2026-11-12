package com.shortx.repository.projection;

import java.time.Instant;

/**
 * Interface projection for the "clicks per day" trend query backing the
 * analytics dashboard's time-series chart.
 */
public interface DailyClickCount {
    Instant getDay();
    Long getCount();
}
