package com.shortx.analytics;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

/**
 * Wraps MaxMind's GeoIP2 Java API for offline (no network call per
 * lookup) IP-to-location resolution.
 *
 * <p><b>Honest limitation, stated plainly:</b> this requires a local
 * GeoLite2-City.mmdb database file that is NOT bundled with this project
 * — it's a MaxMind-licensed binary data file (free tier available, but
 * requires a MaxMind account and license key to download; see
 * https://dev.maxmind.com/geoip/geolite2-free-geolocation-data), and
 * isn't something to fabricate or check into source control. Rather than
 * silently pretend geo analytics works out of the box, this service:
 * <ul>
 *   <li>looks for the file at the path configured by
 *       {@code shortx.geoip.database-path} (default:
 *       {@code /app/data/GeoLite2-City.mmdb}, matching the writable
 *       directory created for the app user in the Dockerfile),</li>
 *   <li>if the file isn't there, logs a clear one-time warning at startup
 *       explaining exactly what's missing and where to get it, and</li>
 *   <li>operates in a disabled state from then on — every lookup returns
 *       {@code Optional.empty()} rather than throwing, so the rest of the
 *       click-recording pipeline (device/browser classification, the
 *       click_event row itself) is completely unaffected by geo data
 *       being unavailable.</li>
 * </ul>
 * This mirrors the same "degrade, don't crash" philosophy already applied
 * to Redis failures in {@code cache.ShortUrlCacheService}.
 */
@Component
@Slf4j
public class GeoIpService {

    @Value("${shortx.geoip.database-path}")
    private String databasePath;

    private DatabaseReader databaseReader;
    private boolean enabled;

    @PostConstruct
    void init() {
        File file = new File(databasePath);
        if (!file.exists()) {
            log.warn("GeoIP database not found at '{}'. Geo analytics (country/city) will be disabled - "
                    + "click events will still be recorded, just without location data. To enable: download "
                    + "GeoLite2-City.mmdb from MaxMind (requires a free account) and place it at this path, "
                    + "or set shortx.geoip.database-path to point at it.", databasePath);
            this.enabled = false;
            return;
        }

        try {
            this.databaseReader = new DatabaseReader.Builder(file).build();
            this.enabled = true;
            log.info("GeoIP database loaded from '{}'", databasePath);
        } catch (IOException e) {
            log.warn("Failed to load GeoIP database at '{}': {}. Geo analytics will be disabled.",
                    databasePath, e.getMessage());
            this.enabled = false;
        }
    }

    public Optional<GeoLocation> resolve(String ipAddress) {
        if (!enabled || ipAddress == null || ipAddress.isBlank()) {
            return Optional.empty();
        }

        try {
            InetAddress address = InetAddress.getByName(ipAddress);
            CityResponse response = databaseReader.city(address);
            String countryCode = response.getCountry() != null ? response.getCountry().getIsoCode() : null;
            String city = response.getCity() != null ? response.getCity().getName() : null;
            return Optional.of(new GeoLocation(countryCode, city));
        } catch (UnknownHostException e) {
            log.debug("Invalid IP address for geo lookup: {}", ipAddress);
            return Optional.empty();
        } catch (GeoIp2Exception e) {
            // Expected for private/reserved/local IPs (e.g. 127.0.0.1,
            // 10.x.x.x during local dev) - MaxMind has no data for these
            // by design, not an error condition worth logging above debug.
            log.debug("No geo data available for IP {}: {}", ipAddress, e.getMessage());
            return Optional.empty();
        } catch (IOException e) {
            log.warn("GeoIP lookup failed for IP {}: {}", ipAddress, e.getMessage());
            return Optional.empty();
        }
    }
}
