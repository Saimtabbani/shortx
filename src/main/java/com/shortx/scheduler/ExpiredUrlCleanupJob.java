package com.shortx.scheduler;

import com.shortx.cache.ShortUrlCacheService;
import com.shortx.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Periodically deactivates short URLs whose expiresAt has passed. Two
 * steps, deliberately in this order and NOT combined into one query:
 * <ol>
 *   <li>Fetch just the short codes about to be deactivated
 *       (ShortUrlRepository.findShortCodesExpiringBefore) — a bulk UPDATE
 *       can't itself report which rows it touched, so this read has to
 *       happen first if we want to evict exactly the right cache
 *       entries.</li>
 *   <li>Evict each of those codes from Redis, then run the bulk
 *       deactivation UPDATE.</li>
 * </ol>
 * Evicting before the DB update (rather than after) is intentional: if
 * this job crashes between steps, the worst outcome is a cache entry
 * evicted slightly early (forcing one extra DB read on the next request
 * for that code, which will correctly find it still active) — never a
 * stale "active" cache entry surviving past a completed deactivation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiredUrlCleanupJob {

    private final ShortUrlRepository shortUrlRepository;
    private final ShortUrlCacheService cacheService;

    @Scheduled(cron = "${shortx.scheduler.expired-cleanup.cron}")
    @Transactional
    public void deactivateExpiredUrls() {
        OffsetDateTime now = OffsetDateTime.now();

        List<String> expiringCodes = shortUrlRepository.findShortCodesExpiringBefore(now);
        if (expiringCodes.isEmpty()) {
            return;
        }

        expiringCodes.forEach(cacheService::evict);

        int deactivatedCount = shortUrlRepository.deactivateExpiredUrls(now);
        log.info("Expired-URL cleanup: deactivated {} short URLs and evicted {} cache entries",
                deactivatedCount, expiringCodes.size());
    }
}
