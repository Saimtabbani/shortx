package com.shortx.repository;

import com.shortx.entity.ShortUrl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code JpaSpecificationExecutor} is included so the service layer can
 * compose dynamic filters (active/expired, custom-alias-only, created-date
 * range, search-by-original-URL) for the "my URLs" listing endpoint
 * without this interface needing a hand-written derived-query method for
 * every combination of optional filters. The Specification-building logic
 * itself lives in the service layer (ShortUrlSpecifications), keeping this
 * interface a pure data-access contract.
 */
public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long>, JpaSpecificationExecutor<ShortUrl> {

    // The single hottest query in the entire system: resolve a short code
    // on every redirect. In practice this is a cache-miss fallback — the
    // service layer checks Redis first — but it must stay fast regardless.
    // Backed by uq_short_urls_short_code.
    Optional<ShortUrl> findByShortCode(String shortCode);

    // Used during creation to detect a collision before insert: for
    // generated (non-custom) codes this should be exceedingly rare given
    // the Snowflake-ID keyspace, but custom aliases are user-chosen and
    // collisions there are expected and must be handled gracefully (409,
    // not a raw constraint-violation stack trace).
    boolean existsByShortCode(String shortCode);

    // "My URLs" dashboard without any filters applied — the common case.
    // Backed by idx_short_urls_user_created.
    Page<ShortUrl> findByUserId(UUID userId, Pageable pageable);

    // Top-N by clicks among currently active links. Backed by the partial
    // index idx_short_urls_click_count (WHERE is_active = true), which is
    // exactly this predicate + sort.
    Page<ShortUrl> findByActiveTrueOrderByClickCountDesc(Pageable pageable);

    /**
     * Atomically increments click_count via a bulk UPDATE rather than
     * load -> mutate -> save. Two important properties of doing it this
     * way:
     * <ol>
     *   <li>It does NOT go through the managed-entity dirty-checking path,
     *       so it does NOT check or increment the {@code @Version} column
     *       — a click on a viral link never collides with a concurrent
     *       settings edit, by design (see the version field's Javadoc on
     *       {@link ShortUrl}).</li>
     *   <li>{@code click_count = click_count + 1} is evaluated by
     *       Postgres itself against the current row value, so concurrent
     *       increments from multiple app instances are safe without any
     *       application-level locking — Postgres's row-level MVCC handles
     *       serializing the individual UPDATE statements.</li>
     * </ol>
     * In practice this is called by the scheduled reconciliation job
     * (which flushes a batched Redis counter into Postgres periodically)
     * rather than on every single redirect request — see the README
     * scalability section for the full read/write path.
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE ShortUrl s SET s.clickCount = s.clickCount + :delta WHERE s.id = :id")
    int incrementClickCount(@Param("id") Long id, @Param("delta") long delta);

    /**
     * Bulk-deactivates expired links in a single statement instead of
     * loading each one into memory. Backed by the partial index
     * idx_short_urls_expiry_sweep, which indexes exactly this predicate
     * (active = true AND expires_at IS NOT NULL) so the sweep never scans
     * the (much larger) set of already-inactive rows.
     */
    /**
     * Fetches just the short codes about to be swept by
     * deactivateExpiredUrls, so the scheduler can evict exactly those
     * entries from the Redis cache before deactivating them in Postgres.
     * Deliberately a separate lightweight query (a bulk UPDATE can't
     * itself report which rows it touched) rather than loading full
     * entities - the scheduler doesn't need anything beyond the code.
     */
    @Query("SELECT s.shortCode FROM ShortUrl s WHERE s.active = true AND s.expiresAt IS NOT NULL AND s.expiresAt < :now")
    List<String> findShortCodesExpiringBefore(@Param("now") OffsetDateTime now);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE ShortUrl s SET s.active = false WHERE s.active = true AND s.expiresAt IS NOT NULL AND s.expiresAt < :now")
    int deactivateExpiredUrls(@Param("now") OffsetDateTime now);
}
