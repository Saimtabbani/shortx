package com.shortx.controller;

import com.shortx.dto.request.CreateShortUrlRequest;
import com.shortx.dto.request.ShortUrlFilterRequest;
import com.shortx.dto.request.UpdateShortUrlRequest;
import com.shortx.dto.response.PageResponse;
import com.shortx.dto.response.ShortUrlResponse;
import com.shortx.security.SecurityUtils;
import com.shortx.service.ShortUrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * Versioned REST API ({@code /api/v1}) for creating and managing short
 * URLs. Deliberately separate from {@link RedirectController}, which
 * serves the actual public-facing redirect at the root path — API
 * management traffic and high-volume redirect traffic have completely
 * different caching, rate-limiting, and monitoring needs, and keeping
 * them in different controllers (and therefore easy to route/scale
 * independently later, e.g. behind different load balancer rules) reflects
 * that.
 *
 * <p><b>Request lifecycle for POST /api/v1/urls</b> (representative of
 * every write endpoint here): the request body is deserialized into
 * {@link CreateShortUrlRequest} by Spring MVC's Jackson message converter
 * → {@code @Valid} triggers Bean Validation against that DTO's
 * constraints (including the custom {@code @ValidUrl}) before the method
 * body ever runs → on violation, Spring throws
 * {@code MethodArgumentNotValidException}, caught by
 * {@code GlobalExceptionHandler} and turned into a 400 with per-field
 * messages, and this method never executes at all → on success,
 * {@link SecurityUtils#currentUserId} reads the authenticated principal
 * (or null for anonymous) from the {@code Authentication} Spring injects
 * from the security context → the service layer does the actual work and
 * either returns a DTO or throws a domain exception, which
 * {@code GlobalExceptionHandler} translates to the appropriate status →
 * this method wraps a successful result in a 201 with a {@code Location}
 * header pointing at the new resource, per REST convention.
 */
@RestController
@RequestMapping("/api/v1/urls")
@RequiredArgsConstructor
@Tag(name = "Short URLs", description = "Create, retrieve, update, and delete shortened URLs")
public class ShortUrlController {

    private final ShortUrlService shortUrlService;

    @PostMapping
    @Operation(summary = "Create a short URL", description = "Anonymous creation is allowed; authenticated requests are associated with the caller's account.")
    public ResponseEntity<ShortUrlResponse> create(
            @Valid @RequestBody CreateShortUrlRequest request,
            Authentication authentication) {
        UUID ownerId = SecurityUtils.currentUserId(authentication);
        ShortUrlResponse response = shortUrlService.create(request, ownerId);
        return ResponseEntity
                .created(URI.create("/api/v1/urls/" + response.getShortCode()))
                .body(response);
    }

    @GetMapping("/{shortCode}")
    @Operation(summary = "Get short URL metadata", description = "Public endpoint; returns metadata (not the destination) even for password-protected or inactive links.")
    public ResponseEntity<ShortUrlResponse> getMetadata(@PathVariable String shortCode) {
        return ResponseEntity.ok(shortUrlService.getByShortCode(shortCode));
    }

    @GetMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List the authenticated user's short URLs", description = "Supports filtering by active status, alias type, creation-date range, and free-text search; paginated and sortable.")
    public ResponseEntity<PageResponse<ShortUrlResponse>> listMine(
            Authentication authentication,
            ShortUrlFilterRequest filter,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID ownerId = SecurityUtils.currentUserId(authentication);
        return ResponseEntity.ok(shortUrlService.listByOwner(ownerId, filter, pageable));
    }

    @GetMapping("/top")
    @Operation(summary = "List top-performing active short URLs", description = "Public leaderboard, ordered by click count descending.")
    public ResponseEntity<PageResponse<ShortUrlResponse>> topUrls(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(shortUrlService.getTopUrls(pageable));
    }

    @PatchMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update a short URL's settings", description = "Partial update: active status, expiry, and password. Owner or admin only.")
    public ResponseEntity<ShortUrlResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateShortUrlRequest request,
            Authentication authentication) {
        UUID requestingUserId = SecurityUtils.currentUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        return ResponseEntity.ok(shortUrlService.update(id, request, requestingUserId, isAdmin));
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete a short URL", description = "Owner or admin only. Cascades to that URL's click history.")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        UUID requestingUserId = SecurityUtils.currentUserId(authentication);
        boolean isAdmin = SecurityUtils.isAdmin(authentication);
        shortUrlService.delete(id, requestingUserId, isAdmin);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
