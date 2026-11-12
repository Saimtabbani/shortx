package com.shortx.analytics;

import com.shortx.common.enums.DeviceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ua_parser.Client;
import ua_parser.Parser;

/**
 * Wraps the ua-parser library (uap-java) to classify a raw User-Agent
 * string into the fields click_events actually stores. The library ships
 * its own regex database on the classpath ({@code ua_parser/regexes.yaml}
 * inside the uap-java jar) — no external file to manage, unlike
 * {@link GeoIpService}'s MaxMind database.
 *
 * <p>ua-parser reports device/OS/browser *family* (e.g. "iPhone", "Mac",
 * "Chrome"), not a device *category* like mobile/tablet/desktop/bot
 * directly. {@link #classifyDevice} is this class's own heuristic on top
 * of that raw family data — documented as a heuristic because it is one;
 * User-Agent-based device classification is inherently approximate (User
 * Agents can be spoofed, and new device families appear faster than any
 * static ruleset can track), which is true of every UA-parsing analytics
 * system, not a shortcoming specific to this implementation.
 */
@Component
@Slf4j
public class UserAgentParser {

    private final Parser parser = new Parser();

    public ParsedUserAgent parse(String userAgentString) {
        if (userAgentString == null || userAgentString.isBlank()) {
            return new ParsedUserAgent(DeviceType.OTHER, null, null);
        }

        try {
            Client client = parser.parse(userAgentString);
            DeviceType deviceType = classifyDevice(client, userAgentString);
            String browser = client.userAgent != null ? client.userAgent.family : null;
            String os = client.os != null ? client.os.family : null;
            return new ParsedUserAgent(deviceType, browser, os);
        } catch (Exception e) {
            // A malformed or unrecognized UA string should degrade to
            // "unknown," not fail the click recording it's part of.
            log.debug("Failed to parse User-Agent '{}': {}", userAgentString, e.getMessage());
            return new ParsedUserAgent(DeviceType.OTHER, null, null);
        }
    }

    private DeviceType classifyDevice(Client client, String rawUserAgent) {
        String deviceFamily = client.device != null ? client.device.family : "";
        String osFamily = client.os != null ? client.os.family : "";
        String lowerUa = rawUserAgent.toLowerCase();

        if (isLikelyBot(deviceFamily, lowerUa)) {
            return DeviceType.BOT;
        }
        if (isLikelyTablet(deviceFamily, lowerUa)) {
            return DeviceType.TABLET;
        }
        if (isLikelyMobile(deviceFamily, osFamily, lowerUa)) {
            return DeviceType.MOBILE;
        }
        return DeviceType.DESKTOP;
    }

    private boolean isLikelyBot(String deviceFamily, String lowerUa) {
        return "Spider".equalsIgnoreCase(deviceFamily)
                || lowerUa.contains("bot")
                || lowerUa.contains("crawler")
                || lowerUa.contains("spider")
                || lowerUa.contains("curl/")
                || lowerUa.contains("wget/");
    }

    private boolean isLikelyTablet(String deviceFamily, String lowerUa) {
        return lowerUa.contains("ipad")
                || (lowerUa.contains("android") && !lowerUa.contains("mobile"))
                || deviceFamily.toLowerCase().contains("tablet");
    }

    private boolean isLikelyMobile(String deviceFamily, String osFamily, String lowerUa) {
        return lowerUa.contains("mobi")
                || "iOS".equalsIgnoreCase(osFamily)
                || (lowerUa.contains("android") && lowerUa.contains("mobile"))
                || !deviceFamily.isBlank() && !"Other".equalsIgnoreCase(deviceFamily) && "Android".equalsIgnoreCase(osFamily);
    }
}
