package com.shortx.service.impl;

import com.shortx.cache.CachedShortUrl;
import com.shortx.cache.ShortUrlCacheService;
import com.shortx.dto.request.CreateShortUrlRequest;
import com.shortx.dto.request.ShortUrlFilterRequest;
import com.shortx.dto.request.UpdateShortUrlRequest;
import com.shortx.dto.response.PageResponse;
import com.shortx.dto.response.ShortUrlResponse;
import com.shortx.entity.ShortUrl;
import com.shortx.exception.AliasAlreadyExistsException;
import com.shortx.exception.InvalidUrlPasswordException;
import com.shortx.exception.ResourceNotFoundException;
import com.shortx.exception.ShortCodeGenerationException;
import com.shortx.exception.UnauthorizedOperationException;
import com.shortx.exception.UrlUnavailableException;
import com.shortx.mapper.ShortUrlMapper;
import com.shortx.repository.ShortUrlRepository;
import com.shortx.repository.UserRepository;
import com.shortx.service.ShortUrlService;
import com.shortx.service.specification.ShortUrlSpecifications;
import com.shortx.util.Base62Encoder;
import com.shortx.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * All dependencies arrive via constructor injection (Lombok's
 * {@code @RequiredArgsConstructor} generates the constructor from the
 * {@code final} fields below) — no field injection, no setter injection.
 * This makes every dependency of this class visible in one place and
 * makes the class trivially constructible in a unit test with plain
 * Mockito mocks, no Spring context required.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShortUrlServiceImpl implements ShortUrlService {

    /**
     * Bounded retry budget for generated short-code collisions. Given the
     * Snowflake ID keyspace this should never actually be exhausted in
     * practice — see {@link ShortCodeGenerationException}'s Javadoc.
     */
    private static final int GENERATION_MAX_ATTEMPTS = 5;

    private final ShortUrlRepository shortUrlRepository;
    private final UserRepository userRepository;
    private final ShortUrlMapper shortUrlMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final PasswordEncoder passwordEncoder;
    private final ShortUrlCacheService cacheService;

    @Value("${shortx.base-url}")
    private String baseUrl;

    @Override
    @Transactional
    public ShortUrlResponse create(CreateShortUrlRequest request, UUID ownerId) {
        boolean hasCustomAlias = request.getCustomAlias() != null && !request.getCustomAlias().isBlank();

        long id;
        String shortCode;

        if (hasCustomAlias) {
            shortCode = request.getCustomAlias();
            if (shortUrlRepository.existsByShortCode(shortCode)) {
                throw new AliasAlreadyExistsException(shortCode);
            }
            id = idGenerator.nextId();
        } else {
            GeneratedCode generated = generateUniqueCode();
            id = generated.id();
            shortCode = generated.code();
        }

        ShortUrl.ShortUrlBuilder builder = ShortUrl.builder()
                .id(id)
                .shortCode(shortCode)
                .originalUrl(request.getOriginalUrl())
                .customAlias(hasCustomAlias)
                .active(true)
                .expiresAt(request.getExpiresAt());

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            builder.passwordHash(passwordEncoder.encode(request.getPassword()));
        }

        if (ownerId != null) {
            // getReferenceById returns a lazy proxy carrying only the ID —
            // this sets the FK correctly without a wasted round-trip to
            // fully load the User just to attach it to a new ShortUrl.
            builder.user(userRepository.getReferenceById(ownerId));
        }

        ShortUrl saved = shortUrlRepository.save(builder.build());
        log.info("Created short URL id={} shortCode={} customAlias={} owner={}",
                saved.getId(), saved.getShortCode(), hasCustomAlias, ownerId);

        return toResponseWithFullUrl(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ShortUrl resolveForRedirect(String shortCode, String rawPassword) {
        Optional<CachedShortUrl> cached = cacheService.get(shortCode);

        ShortUrl shortUrl = cached
                .map(this::fromCached)
                .orElseGet(() -> loadAndCache(shortCode));

        if (!shortUrl.isResolvable()) {
            throw new UrlUnavailableException("This link is no longer active or has expired.");
        }

        if (shortUrl.isPasswordProtected()) {
            if (rawPassword == null || !passwordEncoder.matches(rawPassword, shortUrl.getPasswordHash())) {
                throw new InvalidUrlPasswordException();
            }
        }

        // Only reached after every validation above has passed - a
        // rejected redirect (expired, wrong password) never inflates the
        // click count. Fire-and-forget into the Redis write buffer; see
        // ShortUrlCacheService's Javadoc and scheduler.ClickCountSyncJob
        // for how this eventually lands in Postgres.
        cacheService.incrementClicks(shortUrl.getId());

        return shortUrl;
    }

    /**
     * Loads a short URL from Postgres on a cache miss and populates the
     * cache for subsequent requests. Deliberately caches the row
     * regardless of its active/expiry state — a dead link being probed
     * repeatedly benefits from a cached "no" just as much as a live link
     * benefits from a cached "yes"; the alternative (never caching
     * inactive links) would mean every request to an expired link
     * re-queries Postgres forever.
     */
    private ShortUrl loadAndCache(String shortCode) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("No short URL found for code: " + shortCode));
        cacheService.put(shortUrl);
        return shortUrl;
    }

    /**
     * Reconstructs a detached (unmanaged, never persisted or flushed)
     * ShortUrl purely as a data carrier for the fields the redirect path
     * needs. This is intentionally NOT a Hibernate-managed entity — it's
     * never passed to save()/delete(), and fields irrelevant to
     * redirection (createdAt, version, the lazy user association) are
     * left at their defaults since nothing downstream of this method
     * reads them.
     */
    private ShortUrl fromCached(CachedShortUrl cached) {
        return ShortUrl.builder()
                .id(cached.id())
                .shortCode(cached.shortCode())
                .originalUrl(cached.originalUrl())
                .active(cached.active())
                .customAlias(cached.customAlias())
                .passwordHash(cached.passwordHash())
                .expiresAt(cached.expiresAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ShortUrlResponse getByShortCode(String shortCode) {
        ShortUrl shortUrl = shortUrlRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("No short URL found for code: " + shortCode));
        return toResponseWithFullUrl(shortUrl);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ShortUrlResponse> listByOwner(UUID ownerId, ShortUrlFilterRequest filter, Pageable pageable) {
        Page<ShortUrl> page = shortUrlRepository.findAll(
                ShortUrlSpecifications.ownedByWithFilters(ownerId, filter), pageable);
        return PageResponse.from(page.map(this::toResponseWithFullUrl));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ShortUrlResponse> getTopUrls(Pageable pageable) {
        Page<ShortUrl> page = shortUrlRepository.findByActiveTrueOrderByClickCountDesc(pageable);
        return PageResponse.from(page.map(this::toResponseWithFullUrl));
    }

    @Override
    @Transactional
    public ShortUrlResponse update(Long id, UpdateShortUrlRequest request, UUID requestingUserId, boolean isAdmin) {
        ShortUrl shortUrl = shortUrlRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No short URL found with id: " + id));

        assertOwnership(shortUrl, requestingUserId, isAdmin);

        if (request.getActive() != null) {
            shortUrl.setActive(request.getActive());
        }

        if (request.isClearExpiry()) {
            shortUrl.setExpiresAt(null);
        } else if (request.getExpiresAt() != null) {
            shortUrl.setExpiresAt(request.getExpiresAt());
        }

        if (request.isClearPassword()) {
            shortUrl.setPasswordHash(null);
        } else if (request.getPassword() != null && !request.getPassword().isBlank()) {
            shortUrl.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        // No explicit save() call needed: shortUrl is a managed entity
        // within this @Transactional method, so Hibernate's dirty
        // checking flushes these field changes automatically at commit,
        // as an UPDATE that includes "AND version = ?" - an
        // OptimisticLockException here (mapped to 409 by the global
        // exception handler added in a later milestone) means someone
        // else modified this same URL's settings since it was loaded.
        //
        // Evict rather than re-populate: the next redirect after this
        // update pays one cache-miss DB read, which re-populates Redis
        // with fresh data via loadAndCache(). Simpler and safer than
        // pushing the updated state directly into Redis here and risking
        // it drifting from what Hibernate actually persisted at commit.
        cacheService.evict(shortUrl.getShortCode());

        return toResponseWithFullUrl(shortUrl);
    }

    @Override
    @Transactional
    public void delete(Long id, UUID requestingUserId, boolean isAdmin) {
        ShortUrl shortUrl = shortUrlRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("No short URL found with id: " + id));

        assertOwnership(shortUrl, requestingUserId, isAdmin);

        // Relies on the DB-level ON DELETE CASCADE from click_events to
        // short_urls (V6 migration) rather than a mapped @OneToMany
        // collection on this entity - Postgres enforces the cleanup
        // regardless of what the ORM does or doesn't know about, and we
        // deliberately never mapped that collection (see ShortUrl's
        // Javadoc reasoning on User for the same pattern).
        shortUrlRepository.delete(shortUrl);
        cacheService.evict(shortUrl.getShortCode());
        log.info("Deleted short URL id={} by user={} (admin={})", id, requestingUserId, isAdmin);
    }

    private void assertOwnership(ShortUrl shortUrl, UUID requestingUserId, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        UUID ownerId = shortUrl.ownerId();
        if (ownerId == null || !ownerId.equals(requestingUserId)) {
            throw new UnauthorizedOperationException("You do not have permission to modify this short URL.");
        }
    }

    private GeneratedCode generateUniqueCode() {
        for (int attempt = 1; attempt <= GENERATION_MAX_ATTEMPTS; attempt++) {
            long candidateId = idGenerator.nextId();
            String candidateCode = Base62Encoder.encode(candidateId);
            if (!shortUrlRepository.existsByShortCode(candidateCode)) {
                return new GeneratedCode(candidateId, candidateCode);
            }
            log.warn("Short code collision on attempt {} for generated id {} - retrying", attempt, candidateId);
        }
        throw new ShortCodeGenerationException(
            "Failed to generate a unique short code after " + GENERATION_MAX_ATTEMPTS + " attempts.");
    }

    private ShortUrlResponse toResponseWithFullUrl(ShortUrl shortUrl) {
        ShortUrlResponse response = shortUrlMapper.toResponse(shortUrl);
        response.setFullShortUrl(baseUrl + "/" + shortUrl.getShortCode());
        return response;
    }

    private record GeneratedCode(long id, String code) {
    }
}
