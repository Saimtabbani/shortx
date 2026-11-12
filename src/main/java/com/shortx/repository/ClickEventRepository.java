package com.shortx.repository;

import com.shortx.entity.ClickEvent;
import com.shortx.repository.projection.BreakdownCount;
import com.shortx.repository.projection.DailyClickCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * All queries here filter on short_url_id first (matching
 * idx_click_events_url_time) and aggregate in memory over that bounded
 * result set — see the migration comment on click_events for why this
 * table intentionally does not carry a dedicated index per breakdown
 * dimension.
 */
public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

    // Paginated raw click history for a single URL, newest first — the
    // exact shape idx_click_events_url_time was built for.
    Page<ClickEvent> findByShortUrl_IdOrderByClickedAtDesc(Long shortUrlId, Pageable pageable);

    long countByShortUrl_Id(Long shortUrlId);

    @Query("""
        SELECT c.countryCode AS key, COUNT(c) AS count
        FROM ClickEvent c
        WHERE c.shortUrl.id = :shortUrlId AND c.countryCode IS NOT NULL
        GROUP BY c.countryCode
        ORDER BY COUNT(c) DESC
        """)
    List<BreakdownCount> countByCountry(@Param("shortUrlId") Long shortUrlId);

    @Query("""
        SELECT CAST(c.deviceType AS string) AS key, COUNT(c) AS count
        FROM ClickEvent c
        WHERE c.shortUrl.id = :shortUrlId AND c.deviceType IS NOT NULL
        GROUP BY c.deviceType
        ORDER BY COUNT(c) DESC
        """)
    List<BreakdownCount> countByDeviceType(@Param("shortUrlId") Long shortUrlId);

    @Query("""
        SELECT c.browser AS key, COUNT(c) AS count
        FROM ClickEvent c
        WHERE c.shortUrl.id = :shortUrlId AND c.browser IS NOT NULL
        GROUP BY c.browser
        ORDER BY COUNT(c) DESC
        """)
    List<BreakdownCount> countByBrowser(@Param("shortUrlId") Long shortUrlId);

    @Query("""
        SELECT c.os AS key, COUNT(c) AS count
        FROM ClickEvent c
        WHERE c.shortUrl.id = :shortUrlId AND c.os IS NOT NULL
        GROUP BY c.os
        ORDER BY COUNT(c) DESC
        """)
    List<BreakdownCount> countByOs(@Param("shortUrlId") Long shortUrlId);

    // Daily click trend for the dashboard chart, bounded to a since-date so
    // this never scans a URL's entire click history for an old, popular
    // link.
    @Query(value = """
        SELECT date_trunc('day', clicked_at) AS day, COUNT(*) AS count
        FROM click_events
        WHERE short_url_id = :shortUrlId AND clicked_at >= :since
        GROUP BY day
        ORDER BY day
        """, nativeQuery = true)
    List<DailyClickCount> dailyClickCounts(@Param("shortUrlId") Long shortUrlId, @Param("since") OffsetDateTime since);
}
