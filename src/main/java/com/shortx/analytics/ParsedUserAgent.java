package com.shortx.analytics;

import com.shortx.common.enums.DeviceType;

public record ParsedUserAgent(DeviceType deviceType, String browser, String os) {
}
