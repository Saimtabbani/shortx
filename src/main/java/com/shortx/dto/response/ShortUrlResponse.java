package com.shortx.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Outbound representation of a ShortUrl. Deliberately does NOT expose:
 * the internal Snowflake {@code id} as the primary identifier (shortCode
 * is the public handle; exposing the raw numeric ID leaks creation-order
 * information via its embedded timestamp bits), the owner's full User
 * object (just ownerId, so the frontend can decide "is this mine" without
 * a second lookup), or passwordHash (obviously). isPasswordProtected is a
 * boolean derived from passwordHash's presence — callers can know a
 * password is required without ever seeing the hash.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShortUrlResponse {

    private String shortCode;

    private String fullShortUrl;

    private String originalUrl;

    private boolean customAlias;

    private boolean active;

    private boolean passwordProtected;

    private OffsetDateTime expiresAt;

    private long clickCount;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
