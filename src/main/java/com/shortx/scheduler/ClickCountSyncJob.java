package com.shortx.scheduler;

import com.shortx.cache.ShortUrlCacheService;
import com.shortx.repository.ShortUrlRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Flushes the Redis click-count write buffer (see
 * ShortUrlCacheService's Javadoc) into Postgres. This is the piece that
 * makes the whole "clicks don't hit Postgres per-request" design
 * eventually consistent rather than just... not consistent — without this
 * job, click_count in the database would simply never update.
 *
 * <p>{@code fixedDelay} (not {@code fixedRate}): waits for one run to
 * fully finish before starting the timer for the next. If a sync run were
 * ever slow (e.g. a burst of activity produced an unusually large batch),
 * fixedRate could pile up overlapping executions; fixedDelay can't.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClickCountSyncJob {

    private final ShortUrlCacheService cacheService;
    private final ShortUrlRepository shortUrlRepository;

    @Scheduled(fixedDelayString = "${shortx.scheduler.click-sync.fixed-delay-ms}")
    @Transactional
    public void syncPendingClickCounts() {
        Map<Long, Long> pendingDeltas = cacheService.drainPendingClicks();

        if (pendingDeltas.isEmpty()) {
            return;
        }

        int totalRows = 0;
        for (Map.Entry<Long, Long> entry : pendingDeltas.entrySet()) {
            totalRows += shortUrlRepository.incrementClickCount(entry.getKey(), entry.getValue());
        }

        log.info("Click-count sync: flushed {} pending counters ({} rows updated) from Redis to Postgres",
                pendingDeltas.size(), totalRows);
    }
}
