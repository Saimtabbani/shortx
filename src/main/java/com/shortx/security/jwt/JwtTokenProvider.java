package com.shortx.security.jwt;

import com.shortx.entity.Role;
import com.shortx.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Issues and validates stateless HS256-signed access tokens. Deliberately
 * NOT responsible for refresh tokens — those are opaque, server-tracked,
 * hashed-at-rest values (see entity.RefreshToken /
 * RefreshTokenRepository), a completely different trust model from an
 * access token whose entire point is to be verifiable without a database
 * round trip.
 *
 * <p>The JWT subject is the user's UUID (not their email) — this is the
 * convention {@code security.SecurityUtils} and every controller written
 * in the REST API milestone already depend on via
 * {@code Authentication.getName()}.
 */
@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long accessTokenTtlMillis;

    public JwtTokenProvider(
            @Value("${shortx.jwt.secret}") String secret,
            @Value("${shortx.jwt.access-token-ttl-minutes}") long accessTokenTtlMinutes) {
        // HS256 requires a key of at least 256 bits (32 bytes). The
        // default dev secret in application.yml is long enough, but a
        // misconfigured short production secret should fail loudly at
        // startup rather than silently produce a weak signature.
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            throw new IllegalArgumentException(
                "shortx.jwt.secret must be at least 32 bytes (256 bits) for HS256. Current length: " + keyBytes.length);
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenTtlMillis = accessTokenTtlMinutes * 60_000L;
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        List<String> roleNames = user.getRoles().stream().map(Role::getName).toList();

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("roles", roleNames)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTokenTtlMillis)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parses and validates a token's signature and expiry.
     *
     * @return the parsed claims if the token is valid
     * @throws JwtException if the token is malformed, expired, or has an
     *                       invalid signature — callers (the auth filter)
     *                       treat any JwtException as "not authenticated",
     *                       not a hard error
     */
    public Claims validateAndParseClaims(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            validateAndParseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public UUID extractUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    @SuppressWarnings("unchecked")
    public List<GrantedAuthority> extractAuthorities(Claims claims) {
        List<String> roles = claims.get("roles", List.class);
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .map(String::valueOf)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    /** Convenience for logging/diagnostics; not used in the auth decision path. */
    public boolean isExpired(String token) {
        try {
            validateAndParseClaims(token);
            return false;
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException e) {
            return false;
        }
    }
}
