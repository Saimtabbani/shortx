package com.shortx.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shortx.dto.response.ErrorResponse;
import com.shortx.util.HttpRequestUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.OffsetDateTime;

/**
 * Runs after JwtAuthenticationFilter in the security chain (see
 * SecurityConfig) specifically so {@code SecurityContextHolder} already
 * reflects whether this request is authenticated — that decides both
 * which rate-limit tier applies and how the bucket is keyed:
 * authenticated requests are keyed per-user (so one user's usage never
 * throttles another user sharing the same NAT/office IP), anonymous
 * requests are keyed per-IP (the only identity available for them).
 *
 * <p>Exempts observability/docs endpoints — rate-limiting a health check
 * or Swagger UI protects nothing and only gets in the way of legitimate
 * infrastructure tooling.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.startsWith("/swagger-ui") || path.startsWith("/api-docs");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);

        String key = authenticated
                ? "user:" + authentication.getName()
                : "ip:" + HttpRequestUtils.extractClientIp(request);

        RateLimitDecision decision = rateLimitService.tryConsume(key, authenticated);

        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(decision.remainingTokens(), 0)));

        if (!decision.allowed()) {
            long retryAfterSeconds = Math.max(1, decision.retryAfter().toSeconds());
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

            ErrorResponse body = ErrorResponse.builder()
                    .timestamp(OffsetDateTime.now())
                    .status(HttpStatus.TOO_MANY_REQUESTS.value())
                    .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
                    .message("Rate limit exceeded. Try again in " + retryAfterSeconds + " seconds.")
                    .path(request.getRequestURI())
                    .build();

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), body);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
