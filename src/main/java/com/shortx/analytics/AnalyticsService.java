package com.shortx.analytics;

import com.shortx.dto.response.BreakdownItem;
import com.shortx.dto.response.ClickAnalyticsSummaryResponse;
import com.shortx.dto.response.DailyClickCountItem;
import com.shortx.entity.ShortUrl;
import com.shortx.exception.ResourceNotFoundException;
import com.shortx.exception.UnauthorizedOperationException;
import com.shortx.repository.ClickEventRepository;
import com.shortx.repository.ShortUrlRepository;
import com.shortx.repository.projection.BreakdownCount;
import com.shortx.repository.projection.DailyClickCount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Lives in the {@code analytics} package rather than {@code service} —
 * this is a deliberate boundary, not an accident. {@code ShortUrlService}
 * owns URL CRUD/resolution; this owns reading back what happened to a
 * URL. Splitting them means an analytics-heavy feature (new breakdown
 * dimension, a different aggregation window) never touches
 * ShortUrlServiceImpl, and vice versa.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final ShortUrlRepository shortUrlRepository;
    private final ClickEventRepository clickEventRepository;

    @Transactional(readOnly = true)
    public ClickAnalyticsSummaryResponse getSummary(Long shortUrlId, UUID requestingUserId, boolean isAdmin, int trendDays) {
        ShortUrl shortUrl = shortUrlRepository.findById(shortUrlId)
                .orElseThrow(() -> new ResourceNotFoundException("No short URL found with id: " + shortUrlId));

        assertOwnership(shortUrl, requestingUserId, isAdmin);

        OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusDays(Math.max(trendDays, 1));

        long totalClicks = clickEventRepository.countByShortUrl_Id(shortUrlId);
        List<BreakdownItem> byCountry = toBreakdownItems(clickEventRepository.countByCountry(shortUrlId));
        List<BreakdownItem> byDevice = toBreakdownItems(clickEventRepository.countByDeviceType(shortUrlId));
        List<BreakdownItem> byBrowser = toBreakdownItems(clickEventRepository.countByBrowser(shortUrlId));
        List<BreakdownItem> byOs = toBreakdownItems(clickEventRepository.countByOs(shortUrlId));
        List<DailyClickCountItem> dailyTrend = toDailyTrendItems(clickEventRepository.dailyClickCounts(shortUrlId, since));

        return ClickAnalyticsSummaryResponse.builder()
                .shortCode(shortUrl.getShortCode())
                .totalClicks(totalClicks)
                .byCountry(byCountry)
                .byDevice(byDevice)
                .byBrowser(byBrowser)
                .byOs(byOs)
                .dailyTrend(dailyTrend)
                .build();
    }

    // Duplicates ShortUrlServiceImpl's assertOwnership rather than
    // depending on that class directly - a small (5-line) duplication
    // traded for keeping this service's only dependencies as its own
    // repositories, not another service's internals. If this check grows
    // more complex than a straight equality comparison, it's a good
    // candidate to extract into a shared access-control component both
    // services depend on.
    private void assertOwnership(ShortUrl shortUrl, UUID requestingUserId, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        UUID ownerId = shortUrl.ownerId();
        if (ownerId == null || !ownerId.equals(requestingUserId)) {
            throw new UnauthorizedOperationException("You do not have permission to view analytics for this short URL.");
        }
    }

    private List<BreakdownItem> toBreakdownItems(List<BreakdownCount> rows) {
        return rows.stream()
                .map(row -> BreakdownItem.builder().key(row.getKey()).count(row.getCount()).build())
                .toList();
    }

    private List<DailyClickCountItem> toDailyTrendItems(List<DailyClickCount> rows) {
        return rows.stream()
                .map(row -> DailyClickCountItem.builder()
                        .day(row.getDay().atOffset(ZoneOffset.UTC).toLocalDate())
                        .count(row.getCount())
                        .build())
                .toList();
    }
}
