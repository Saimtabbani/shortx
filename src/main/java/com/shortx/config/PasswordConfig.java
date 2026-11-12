package com.shortx.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Split out from the main Spring Security filter-chain config (added in
 * the Security milestone) on purpose: the password encoder is needed by
 * the URL-password-protection feature (hashing a short URL's access
 * password) just as much as by user account authentication, and neither
 * of those is really "the security filter chain." Keeping it in its own
 * small config class avoids a false dependency where ShortUrlService would
 * otherwise need to depend on the entire security configuration just to
 * hash a string.
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Strength 12: meaningfully more expensive than BCrypt's default
        // of 10 (roughly 4x the hashing cost) without yet being slow
        // enough to matter for interactive request latency. Revisit this
        // number periodically as hardware gets faster — that's the whole
        // point of BCrypt's tunable cost factor.
        return new BCryptPasswordEncoder(12);
    }
}
