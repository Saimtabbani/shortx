package com.shortx.controller;

import com.shortx.analytics.ClickEventRecorder;
import com.shortx.entity.ShortUrl;
import com.shortx.service.ShortUrlService;
import com.shortx.util.HttpRequestUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Serves the actual redirect: {@code GET /{shortCode}} → 302 to the
 * original URL. This is the highest-traffic, most latency-sensitive
 * endpoint in the entire system — every other endpoint in this project
 * exists to manage data that ultimately gets read here, so this
 * controller is kept intentionally minimal: no DTO mapping, no pagination,
 * just "resolve, then redirect."
 *
 * <p>The path pattern is constrained to
 * {@code {shortCode:[a-zA-Z0-9_-]{3,20}}} rather than a bare
 * {@code {shortCode}}. Two reasons: (1) it matches the exact character
 * set short codes are ever generated or allowed as custom aliases from
 * (see CreateShortUrlRequest's @Pattern constraint and
 * util.Base62Encoder's alphabet), so a request that can't possibly match
 * a real short code 404s immediately without even querying the database;
 * and (2) it prevents this catch-all single-segment route from ever
 * matching paths like {@code /swagger-ui.html} or {@code /favicon.ico},
 * which contain a literal '.' outside the allowed character class. Spring
 * MVC's handler mapping already prefers literal path matches (like
 * springdoc's own {@code /swagger-ui.html} mapping) over variable ones
 * when both technically match, but this constraint removes the ambiguity
 * entirely rather than relying on that precedence rule holding.
 *
 * <p>In a real high-traffic deployment, this controller's endpoint would
 * typically live behind its own subdomain or dedicated route at the load
 * balancer (e.g. a bare {@code shrt.link/{code}} host serving only
 * redirects, fronted by a CDN/edge cache keyed on the short code) so it
 * can be scaled, cached, and rate-limited completely independently of the
 * management API in {@link ShortUrlController}. Documented here rather
 * than implemented, since that's infrastructure/deployment topology, not
 * application code.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Redirect", description = "Public short-link redirection")
public class RedirectController {

    private final ShortUrlService shortUrlService;
    private final ClickEventRecorder clickEventRecorder;

    @GetMapping("/{shortCode:[a-zA-Z0-9_-]{3,20}}")
    @Operation(
        summary = "Redirect to the destination URL",
        description = "Returns 302 Found with a Location header on success. Password-protected links require a `password` query parameter and return 401 if it's missing or wrong; expired/deactivated links return 410 Gone."
    )
    public ResponseEntity<Void> redirect(
            @PathVariable String shortCode,
            @RequestParam(required = false) String password,
            HttpServletRequest request) {

        ShortUrl shortUrl = shortUrlService.resolveForRedirect(shortCode, password);

        // Fire-and-forget: record() is @Async, so this call returns
        // immediately and the click_events write happens on a background
        // thread (see analytics.ClickEventRecorder / config.AsyncConfig).
        // The redirect response below is built and returned without
        // waiting on it.
        clickEventRecorder.record(
                shortUrl.getId(),
                HttpRequestUtils.extractClientIp(request),
                request.getHeader("User-Agent"),
                request.getHeader("Referer"));

        return ResponseEntity
                .status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create(shortUrl.getOriginalUrl()).toString())
                // Redirect targets can change (a user might update where
                // an alias points, or deactivate it) - instructing
                // intermediary/browser caches not to cache this response
                // means a change takes effect immediately instead of
                // being masked by a stale cached redirect.
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}
