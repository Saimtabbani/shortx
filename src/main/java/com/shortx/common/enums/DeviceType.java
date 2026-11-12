package com.shortx.common.enums;

/**
 * Device category resolved from the User-Agent string at click time
 * (see analytics.UserAgentParser). Stored on click_events.device_type for
 * fast GROUP BY breakdowns without re-parsing raw user-agent strings.
 */
public enum DeviceType {
    DESKTOP,
    MOBILE,
    TABLET,
    BOT,
    OTHER
}
