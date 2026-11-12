package com.shortx.service;

import com.shortx.dto.request.CreateShortUrlRequest;
import com.shortx.dto.request.ShortUrlFilterRequest;
import com.shortx.dto.request.UpdateShortUrlRequest;
import com.shortx.dto.response.PageResponse;
import com.shortx.dto.response.ShortUrlResponse;
import com.shortx.entity.ShortUrl;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface ShortUrlService {

    /**
     * Creates a new short URL. {@code ownerId} is nullable — anonymous
     * creation is allowed (see V5 migration: short_urls.user_id is
     * nullable), subject to the stricter anonymous rate-limit tier.
     */
    ShortUrlResponse create(CreateShortUrlRequest request, UUID ownerId);

    /**
     * Resolves a short code to its live {@link ShortUrl} entity for
     * redirect purposes. Enforces active/expiry status and password
     * protection. Returns the entity (not a DTO) because the redirect
     * controller needs the raw originalUrl and this is an internal,
     * request-scoped hop — not a value returned to an API consumer.
     *
     * @param rawPassword the password supplied by the caller, or null if
     *                     none was supplied. Ignored if the link isn't
     *                     password-protected.
     * @throws com.shortx.exception.ResourceNotFoundException if no URL matches shortCode
     * @throws com.shortx.exception.UrlUnavailableException if the URL is inactive or expired
     * @throws com.shortx.exception.InvalidUrlPasswordException if password-protected and rawPassword is missing/wrong
     */
    ShortUrl resolveForRedirect(String shortCode, String rawPassword);

    ShortUrlResponse getByShortCode(String shortCode);

    PageResponse<ShortUrlResponse> listByOwner(UUID ownerId, ShortUrlFilterRequest filter, Pageable pageable);

    PageResponse<ShortUrlResponse> getTopUrls(Pageable pageable);

    /**
     * Applies a partial update. {@code requestingUserId}/{@code isAdmin}
     * enforce row-level ownership: a non-admin caller may only modify a
     * URL they own.
     */
    ShortUrlResponse update(Long id, UpdateShortUrlRequest request, UUID requestingUserId, boolean isAdmin);

    void delete(Long id, UUID requestingUserId, boolean isAdmin);
}
