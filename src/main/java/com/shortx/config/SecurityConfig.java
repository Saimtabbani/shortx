package com.shortx.config;

import com.shortx.security.jwt.JwtAccessDeniedHandler;
import com.shortx.security.jwt.JwtAuthenticationEntryPoint;
import com.shortx.security.jwt.JwtAuthenticationFilter;
import com.shortx.security.ratelimit.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * This is the config that finally resolves the interim state called out
 * at the end of the REST API milestone: with no SecurityFilterChain bean,
 * Spring Boot's default auto-configuration required authentication on
 * every request. This bean replaces that default entirely.
 *
 * <p>Authorization rules are listed most-specific-first, ending in a
 * catch-all {@code anyRequest().authenticated()} — Spring Security
 * evaluates rules in declaration order and uses the first match, so this
 * ordering is load-bearing, not stylistic.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // enables @PreAuthorize for future role-gated endpoints
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final RateLimitFilter rateLimitFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // stateless token-based API - no session cookie for CSRF to target
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                .accessDeniedHandler(jwtAccessDeniedHandler))
            .authorizeHttpRequests(auth -> auth
                // --- Auth endpoints: always public (you can't log in
                // while already required to be logged in) ---
                .requestMatchers("/api/v1/auth/**").permitAll()

                // --- Observability / API docs ---
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/api-docs/**", "/api-docs").permitAll()

                // --- URL creation: explicitly public. The controller
                // itself (via SecurityUtils.currentUserId) still attaches
                // an owner when a valid token IS present - permitAll here
                // only means a token is not REQUIRED, not that it's
                // ignored. ---
                .requestMatchers(HttpMethod.POST, "/api/v1/urls").permitAll()

                // --- Public reads: metadata-by-code and the top-URLs
                // leaderboard are both single path segments under
                // /api/v1/urls/, matching this one pattern. ---
                .requestMatchers(HttpMethod.GET, "/api/v1/urls/*").permitAll()

                // --- The actual redirect endpoint. Matches exactly the
                // same shape as RedirectController's own path constraint
                // (single segment); see that controller's Javadoc for why
                // that constraint makes this rule unambiguous rather than
                // an overly broad wildcard. ---
                .requestMatchers(HttpMethod.GET, "/*").permitAll()

                // --- Everything else (list-mine, update, delete, and any
                // future authenticated-only endpoint) requires a valid
                // access token. ---
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            // Rate limiting runs right after JWT auth so it already knows
            // whether the request is authenticated (deciding both the
            // tier and how the bucket is keyed) - see RateLimitFilter's
            // Javadoc.
            .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        // Backed by CustomUserDetailsService + the PasswordConfig bean via
        // Spring Boot's auto-configured DaoAuthenticationProvider - used
        // only by AuthService.login(), nowhere else.
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // "*" is appropriate for a public API/portfolio deployment where
        // any frontend origin should be able to call this service. A
        // production deployment serving a specific first-party frontend
        // should narrow this to that exact origin list instead.
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
