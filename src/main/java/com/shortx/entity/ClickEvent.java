package com.shortx.entity;

import com.shortx.common.enums.DeviceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * A single append-only click record. Like {@link ShortUrl}, the ID is a
 * pre-assigned Snowflake ID (set by the service layer before persist) —
 * see the same rationale there.
 *
 * <p>This entity is written once and never updated — there is no
 * {@code @Version} field because optimistic locking exists to detect
 * concurrent conflicting writes, and an append-only row by definition has
 * no second writer to conflict with.
 */
@Entity
@Table(name = "click_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "shortUrl")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ClickEvent {

    @Id
    @EqualsAndHashCode.Include
    private Long id;

    // LAZY: analytics queries almost always go the other direction
    // (ShortUrlRepository/ClickEventRepository filtering WHERE
    // short_url_id = ?), so eagerly joining the parent on every click-event
    // load would be wasted work in the dominant access pattern.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "short_url_id", nullable = false)
    private ShortUrl shortUrl;

    @CreationTimestamp
    @Column(name = "clicked_at", nullable = false, updatable = false)
    private OffsetDateTime clickedAt;

    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(length = 100)
    private String city;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", length = 20)
    private DeviceType deviceType;

    @Column(length = 50)
    private String browser;

    @Column(length = 50)
    private String os;

    @Column(length = 500)
    private String referrer;

    @Column(name = "user_agent", length = 500)
    private String userAgent;
}
