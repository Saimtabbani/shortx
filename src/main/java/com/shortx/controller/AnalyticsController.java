package com.shortx.controller;

import com.shortx.analytics.AnalyticsService;
import com.shortx.dto.response.ClickAnalyticsSummaryResponse;
import com.shortx.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Nested under {@code /api/v1/urls/{id}/analytics} rather than a flat
 * {@code /api/v1/analytics/{id}} — the URL path itself communicates that
 * analytics belong to a specific short URL, and this path shape doesn't
 * collide with any pattern in {@link ShortUrlController}'s security rules
 * (see SecurityConfig: this two-segment-after-/urls/ path doesn't match
 * the single-segment {@code /api/v1/urls/*} public-read pattern, so it
 * correctly falls through to requiring authentication by default —
 * ownership is then enforced inside AnalyticsService itself).
 */
@RestController
@RequestMapping("/api/v1/urls/{id}/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Click analytics for a short URL: totals, geo/device/browser breakdowns, daily trend")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get click analytics for a short URL", description = "Owner or admin only. Includes total clicks, country/device/browser/OS breakdowns, and a daily click trend.")
    public ResponseEntity<ClickAnalyticsSummaryResponse> getSummary(
            @PathVariable Long id,
            @RequestParam(defaultValue = "30") int trendDays,
            Authentication authentication) {
        UUID requestingUserId = SecurityUtils.currentUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ResponseEntity.ok(analyticsService.getSummary(id, requestingUserId, isAdmin, trendDays));
    }
}
