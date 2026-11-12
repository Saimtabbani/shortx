package com.shortx.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The core domain entity: a shortened URL.
 *
 * <p><b>ID strategy:</b> deliberately NO {@code @GeneratedValue}. The ID
 * must be a pre-assigned Snowflake ID (see util.SnowflakeIdGenerator)
 * because Base62-encoding it produces the public short_code — the code
 * has to exist before the row is ever inserted, not be handed back by the
 * database afterward. The service layer is responsible for calling
 * {@code idGenerator.nextId()} and setting it on this entity before
 * {@code save()}. (An alternative considered: a custom Hibernate
 * {@code IdentifierGenerator} wired through Hibernate's ID-generation
 * SPI, which would make the assignment "automatic" on
 * {@code persist()}. Rejected here in favor of explicit assignment in the
 * service layer — it's a few extra lines at each creation call site in
 * exchange for not routing ID generation through Hibernate internals via
 * a static ApplicationContext holder, which is harder to unit-test and
 * harder for a new engineer reading the code to trace.)
 *
 * <p><b>version:</b> optimistic lock for metadata edits (expiry, password,
 * active flag). click_count is updated via a separate repository method
 * using a native/JPQL bulk UPDATE that intentionally does NOT go through
 * the entity's managed version-check path — see ShortUrlRepository and
 * the migration comment on this column for the full rationale.
 */
@Entity
@Table(name = "short_urls")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "user")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ShortUrl {

    @Id
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "short_code", nullable = false, unique = true, length = 20)
    private String shortCode;

    @Column(name = "original_url", nullable = false, columnDefinition = "TEXT")
    private String originalUrl;

    @Column(name = "is_custom_alias", nullable = false)
    @Builder.Default
    private boolean customAlias = false;

    // Nullable: anonymous URL creation is allowed. LAZY because most
    // reads of a ShortUrl (the hot redirect path in particular) don't need
    // the owner at all.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "click_count", nullable = false)
    @Builder.Default
    private long clickCount = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public boolean isPasswordProtected() {
        return passwordHash != null;
    }

    public boolean isExpired() {
        return expiresAt != null && OffsetDateTime.now().isAfter(expiresAt);
    }

    /** A link is usable for redirect purposes only if active AND not expired. */
    public boolean isResolvable() {
        return active && !isExpired();
    }

    public UUID ownerId() {
        return user != null ? user.getId() : null;
    }
}
