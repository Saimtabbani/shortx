package com.shortx.controller;

import com.shortx.dto.request.LoginRequest;
import com.shortx.dto.request.RefreshTokenRequest;
import com.shortx.dto.request.RegisterRequest;
import com.shortx.dto.response.AuthResponse;
import com.shortx.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * All four endpoints here are unauthenticated by design (see
 * SecurityConfig's {@code /api/v1/auth/**} permitAll rule) — that's the
 * whole point of an auth endpoint. {@code HttpServletRequest} is injected
 * directly (not through a DTO) purely to read the User-Agent/IP for the
 * refresh token's device metadata; it never reaches the service layer's
 * business logic beyond that.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, login, and token refresh")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new account", description = "Creates a user with the default ROLE_USER role and immediately returns a token pair, logging the new user in.")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        AuthResponse response = authService.register(request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in", description = "Returns a new access/refresh token pair on success.")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, httpRequest));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh the access token", description = "Rotates the presented refresh token: the old one is revoked and a new access/refresh pair is issued. The old token cannot be reused.")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.refresh(request, httpRequest));
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out", description = "Revokes the given refresh token. Idempotent — succeeds even if the token was already invalid.")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }
}
