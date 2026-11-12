package com.shortx.security.ratelimit;

import java.time.Duration;

public record RateLimitDecision(
        boolean allowed,
        long remainingTokens,
        long limit,
        Duration retryAfter
) {
}
