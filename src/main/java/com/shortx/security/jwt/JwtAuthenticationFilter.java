package com.shortx.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Runs once per request, ahead of Spring Security's own
 * UsernamePasswordAuthenticationFilter (see SecurityConfig). If a valid
 * Bearer token is present, populates the SecurityContext directly from
 * the token's claims — no database lookup, no call into
 * CustomUserDetailsService. This is what makes JWT auth actually
 * stateless: authenticating a request costs a signature verification,
 * not a query.
 *
 * <p>Deliberately permissive on failure: a missing, malformed, or expired
 * token does NOT throw or short-circuit the filter chain here. It simply
 * leaves the SecurityContext unauthenticated and lets the request
 * continue — whether that matters depends entirely on whether the
 * requested endpoint actually requires authentication, which is
 * SecurityConfig's decision to make, not this filter's. This is also
 * exactly why anonymous short-URL creation (POST /api/v1/urls with no
 * token) and authenticated creation (same endpoint, with a token) can
 * share one permitAll-ish rule while still correctly distinguishing owner
 * vs. anonymous inside the controller.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null) {
            try {
                Claims claims = jwtTokenProvider.validateAndParseClaims(token);
                UUID userId = jwtTokenProvider.extractUserId(claims);
                List<GrantedAuthority> authorities = jwtTokenProvider.extractAuthorities(claims);

                // principal = userId.toString(), NOT a UserDetails/UserPrincipal
                // instance — there is deliberately no DB-backed principal
                // object here. SecurityUtils.currentUserId parses
                // Authentication.getName() right back into a UUID, which
                // is all any controller in this codebase actually needs.
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        userId.toString(), null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException e) {
                // Malformed/expired/invalid signature - log at debug (this
                // is routine traffic, e.g. a client with a stale token,
                // not an application error) and fall through unauthenticated.
                log.debug("Rejected invalid JWT on {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTH_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
