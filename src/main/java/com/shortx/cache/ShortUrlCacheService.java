package com.shortx.cache;

import com.shortx.entity.ShortUrl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Encapsulates every Redis interaction in the system behind a small,
 * purpose-built API. Nothing outside this class knows a key naming
 * scheme, a serialization format, or that Redis is even involved — the
 * service layer calls {@code get}/{@code put}/{@code evict}/{@code
 * incrementClicks} and doesn't know or care that any of it is backed by
 * Redis rather than, say, an in-memory Caffeine cache. That's the whole
 * point of isolating this in its own package: swapping the cache
 * implementation later touches this one class, not every call site.
 *
 * <h2>Two independent Redis usages, on purpose</h2>
 * <ol>
 *   <li><b>Cache-aside for redirect resolution</b> ({@code shorturl:*}
 *       keys, TTL-bounded, JSON via RedisTemplate): read-through on
 *       cache miss, explicit eviction on update/delete, TTL as a safety
 *       net for anything eviction doesn't catch (see the scheduler
 *       milestone's ExpiredUrlCleanupJob for the one deliberate
 *       exception to "evict on write").</li>
 *   <li><b>The click counter</b> ({@code clicks:pending:*} keys, plain
 *       INCR via StringRedisTemplate, no TTL): NOT a cache of anything —
 *       it's a write buffer. Every redirect increments an in-Redis
 *       counter instead of writing to Postgres directly, which is the
 *       concrete mechanism behind every "click_count updates don't hit
 *       Postgres per-request" comment left in the Database/Entity/
 *       Repository/Service milestones. A separate scheduled job (see
 *       scheduler.ClickCountSyncJob) periodically drains these counters
 *       into Postgres via the bulk-UPDATE repository method built for
 *       exactly this purpose.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShortUrlCacheService {

    private static final String CACHE_KEY_PREFIX = "shorturl:";
    private static final String CLICK_COUNTER_PREFIX = "clicks:pending:";
    private static final String PENDING_IDS_SET_KEY = "clicks:pending:ids";

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${shortx.cache.short-url-ttl-minutes}")
    private long ttlMinutes;

    // --- Cache-aside: redirect resolution ---------------------------------

    public Optional<CachedShortUrl> get(String shortCode) {
        try {
            Object value = redisTemplate.opsForValue().get(cacheKey(shortCode));
            if (value instanceof CachedShortUrl cached) {
                return Optional.of(cached);
            }
            return Optional.empty();
        } catch (Exception e) {
            // A Redis outage should degrade this service to "always hit
            // Postgres," not take it down entirely. Any cache read
            // failure is treated as a miss - the service layer falls
            // through to the database exactly as it would on a real miss.
            log.warn("Redis read failed for shortCode={}, falling back to database: {}", shortCode, e.getMessage());
            return Optional.empty();
        }
    }

    public void put(ShortUrl shortUrl) {
        try {
            CachedShortUrl cached = new CachedShortUrl(
                    shortUrl.getId(),
                    shortUrl.getShortCode(),
                    shortUrl.getOriginalUrl(),
                    shortUrl.isActive(),
                    shortUrl.isCustomAlias(),
                    shortUrl.getPasswordHash(),
                    shortUrl.getExpiresAt());
            redisTemplate.opsForValue().set(cacheKey(shortUrl.getShortCode()), cached, Duration.ofMinutes(ttlMinutes));
        } catch (Exception e) {
            // Same principle as get(): a failed cache WRITE must never
            // fail the request that triggered it. The row was already
            // successfully read from/written to Postgres by the time this
            // is called - Redis is purely an optimization layer here.
            log.warn("Redis write failed for shortCode={}: {}", shortUrl.getShortCode(), e.getMessage());
        }
    }

    public void evict(String shortCode) {
        try {
            redisTemplate.delete(cacheKey(shortCode));
        } catch (Exception e) {
            log.warn("Redis eviction failed for shortCode={}: {}", shortCode, e.getMessage());
        }
    }

    // --- Click counter write buffer -----------------------------------

    public void incrementClicks(Long shortUrlId) {
        try {
            stringRedisTemplate.opsForValue().increment(counterKey(shortUrlId));
            // Tracked separately so the reconciliation job knows exactly
            // which counters exist without an expensive KEYS/SCAN over
            // the whole keyspace.
            stringRedisTemplate.opsForSet().add(PENDING_IDS_SET_KEY, shortUrlId.toString());
        } catch (Exception e) {
            // A dropped click-count increment is an acceptable loss (it
            // only affects an analytics number, never the redirect
            // itself, which has already been served by this point in the
            // call chain) - logged, not propagated.
            log.warn("Failed to increment Redis click counter for shortUrlId={}: {}", shortUrlId, e.getMessage());
        }
    }

    /**
     * Atomically reads and clears every pending click counter, returning
     * {@code shortUrlId -> pending delta}. Called exclusively by
     * scheduler.ClickCountSyncJob. Uses GETDEL (via
     * {@code opsForValue().getAndDelete}) per counter so a counter can
     * never be double-counted by two overlapping sync runs — the read and
     * the clear are one atomic Redis operation, not a separate GET then
     * DEL that could race.
     */
    public Map<Long, Long> drainPendingClicks() {
        Set<String> pendingIds = stringRedisTemplate.opsForSet().members(PENDING_IDS_SET_KEY);
        if (pendingIds == null || pendingIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> deltas = new HashMap<>();
        for (String idStr : pendingIds) {
            String raw = stringRedisTemplate.opsForValue().getAndDelete(counterKey(Long.parseLong(idStr)));
            if (raw != null) {
                long delta = Long.parseLong(raw);
                if (delta > 0) {
                    deltas.put(Long.parseLong(idStr), delta);
                }
            }
            stringRedisTemplate.opsForSet().remove(PENDING_IDS_SET_KEY, idStr);
        }
        return deltas;
    }

    private String cacheKey(String shortCode) {
        return CACHE_KEY_PREFIX + shortCode;
    }

    private String counterKey(Long shortUrlId) {
        return CLICK_COUNTER_PREFIX + shortUrlId;
    }
}
