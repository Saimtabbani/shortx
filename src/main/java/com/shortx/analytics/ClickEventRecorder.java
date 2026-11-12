package com.shortx.analytics;

import com.shortx.entity.ClickEvent;
import com.shortx.repository.ClickEventRepository;
import com.shortx.repository.ShortUrlRepository;
import com.shortx.util.HashUtils;
import com.shortx.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * The one place a click actually gets written to click_events. Called by
 * {@link com.shortx.controller.RedirectController} immediately after a
 * successful {@code ShortUrlService.resolveForRedirect} — deliberately
 * NOT called from within ShortUrlServiceImpl itself. Recording "what
 * happened on this HTTP request" (IP, User-Agent, referrer) is a
 * different concern from "resolve this code to a URL," and keeping them
 * separate means ShortUrlService stays free of any HttpServletRequest-
 * shaped parameters — see RedirectController's Javadoc update for the
 * call-site reasoning.
 *
 * <p>{@code @Async}: the redirect response has already been fully decided
 * (redirect vs. 410/401) before this method is even invoked, and nothing
 * about analytics recording should add latency to the redirect itself or
 * risk failing it. If this write fails, the person still gets redirected
 * — they just don't show up in tomorrow's dashboard, which is the right
 * trade-off for an analytics side-effect on the hottest path in the
 * system.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClickEventRecorder {

    private final ClickEventRepository clickEventRepository;
    private final ShortUrlRepository shortUrlRepository;
    private final SnowflakeIdGenerator idGenerator;
    private final UserAgentParser userAgentParser;
    private final GeoIpService geoIpService;

    @Async
    @Transactional
    public void record(Long shortUrlId, String ipAddress, String userAgentString, String referrer) {
        try {
            ParsedUserAgent parsedUa = userAgentParser.parse(userAgentString);
            Optional<GeoLocation> geo = geoIpService.resolve(ipAddress);

            ClickEvent event = ClickEvent.builder()
                    .id(idGenerator.nextId())
                    // getReferenceById: a lazy proxy carrying only the ID.
                    // We already know this ID is valid - it came from a
                    // ShortUrl that ShortUrlService just successfully
                    // resolved a moment ago - so there's no need to
                    // re-fetch and validate the full row here.
                    .shortUrl(shortUrlRepository.getReferenceById(shortUrlId))
                    .ipHash(ipAddress != null ? HashUtils.sha256Hex(ipAddress) : null)
                    .countryCode(geo.map(GeoLocation::countryCode).orElse(null))
                    .city(geo.map(GeoLocation::city).orElse(null))
                    .deviceType(parsedUa.deviceType())
                    .browser(parsedUa.browser())
                    .os(parsedUa.os())
                    .referrer(truncate(referrer, 500))
                    .userAgent(truncate(userAgentString, 500))
                    .build();

            clickEventRepository.save(event);
        } catch (Exception e) {
            // See class Javadoc: a failed click-event write must never
            // propagate anywhere that could affect the (already-served)
            // redirect. Logged here in addition to AsyncConfig's global
            // handler for context specific to which shortUrlId failed.
            log.error("Failed to record click event for shortUrlId={}: {}", shortUrlId, e.getMessage(), e);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
