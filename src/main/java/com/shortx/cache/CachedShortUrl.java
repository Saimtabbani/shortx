package com.shortx.cache;

import java.time.OffsetDateTime;

/**
 * The value actually stored in Redis for the redirect hot path.
 * Deliberately NOT the {@code ShortUrl} JPA entity — caching a managed
 * entity directly is a well-known trap: it carries a lazy {@code user}
 * association that either fails to serialize or drags in far more data
 * than a redirect needs, and it couples the cache format to whatever
 * Hibernate/JPA annotations happen to be on the entity today.
 *
 * <p>Carries exactly the fields {@code resolveForRedirect} needs to
 * validate and serve a redirect without touching the database: no
 * {@code createdAt}/{@code updatedAt}/{@code version}, none of which the
 * hot path cares about.
 */
public record CachedShortUrl(
        Long id,
        String shortCode,
        String originalUrl,
        boolean active,
        boolean customAlias,
        String passwordHash,
        OffsetDateTime expiresAt
) {

    public boolean isPasswordProtected() {
        return passwordHash != null;
    }

    public boolean isExpired() {
        return expiresAt != null && OffsetDateTime.now().isAfter(expiresAt);
    }

    public boolean isResolvable() {
        return active && !isExpired();
    }
}
