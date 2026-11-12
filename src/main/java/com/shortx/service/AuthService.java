package com.shortx.service;

import com.shortx.dto.request.LoginRequest;
import com.shortx.dto.request.RefreshTokenRequest;
import com.shortx.dto.request.RegisterRequest;
import com.shortx.dto.response.AuthResponse;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {

    AuthResponse register(RegisterRequest request, HttpServletRequest httpRequest);

    AuthResponse login(LoginRequest request, HttpServletRequest httpRequest);

    /** Rotates the presented refresh token: revokes it and issues a new access/refresh pair. */
    AuthResponse refresh(RefreshTokenRequest request, HttpServletRequest httpRequest);

    /** Idempotent: revokes the given refresh token if it exists and is still valid; no error if not. */
    void logout(RefreshTokenRequest request);
}
