package com.shortx.service.impl;

import com.shortx.common.constants.RoleConstants;
import com.shortx.dto.request.LoginRequest;
import com.shortx.dto.request.RefreshTokenRequest;
import com.shortx.dto.request.RegisterRequest;
import com.shortx.dto.response.AuthResponse;
import com.shortx.entity.RefreshToken;
import com.shortx.entity.Role;
import com.shortx.entity.User;
import com.shortx.exception.EmailAlreadyExistsException;
import com.shortx.exception.InvalidCredentialsException;
import com.shortx.exception.InvalidRefreshTokenException;
import com.shortx.mapper.UserMapper;
import com.shortx.repository.RefreshTokenRepository;
import com.shortx.repository.RoleRepository;
import com.shortx.repository.UserRepository;
import com.shortx.security.UserPrincipal;
import com.shortx.security.jwt.JwtTokenProvider;
import com.shortx.service.AuthService;
import com.shortx.util.HashUtils;
import com.shortx.util.HttpRequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserMapper userMapper;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${shortx.jwt.access-token-ttl-minutes}")
    private long accessTokenTtlMinutes;

    @Value("${shortx.jwt.refresh-token-ttl-days}")
    private long refreshTokenTtlDays;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        String email = normalize(request.getEmail());

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        Role userRole = roleRepository.findByName(RoleConstants.ROLE_USER)
                // If this ever fires, the V2 migration's seed data is
                // missing/corrupted - a deploy-time data problem, not a
                // user-facing one, hence the unchecked IllegalStateException
                // rather than a dedicated exception type callers would
                // need to handle.
                .orElseThrow(() -> new IllegalStateException(
                    "Required role " + RoleConstants.ROLE_USER + " is not seeded in the database."));

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .enabled(true)
                .build();
        user.addRole(userRole);

        User saved = userRepository.save(user);
        log.info("Registered new user id={} email={}", saved.getId(), email);

        return issueTokenPair(saved, httpRequest);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String email = normalize(request.getEmail());

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        } catch (BadCredentialsException | DisabledException e) {
            // Both "wrong password" and "unknown email" surface as
            // BadCredentialsException (see CustomUserDetailsService);
            // DisabledException covers a soft-disabled account. All three
            // collapse to the same generic message - see
            // InvalidCredentialsException's Javadoc for why.
            throw new InvalidCredentialsException();
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        log.info("User id={} logged in", principal.getId());

        return issueTokenPair(principal.getUser(), httpRequest);
    }

    @Override
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request, HttpServletRequest httpRequest) {
        String hash = HashUtils.sha256Hex(request.getRefreshToken());

        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (!existing.isValid()) {
            throw new InvalidRefreshTokenException();
        }

        // Rotation: the presented token is single-use. Revoking it here
        // means a stolen-and-replayed refresh token fails on its second
        // use - the legitimate client will already have moved on to the
        // new token from this response, so the only party ever presenting
        // the old one again is an attacker.
        existing.setRevoked(true);
        refreshTokenRepository.save(existing);

        User user = existing.getUser();
        log.info("Rotated refresh token for user id={}", user.getId());

        return issueTokenPair(user, httpRequest);
    }

    @Override
    @Transactional
    public void logout(RefreshTokenRequest request) {
        String hash = HashUtils.sha256Hex(request.getRefreshToken());
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
        // No exception if the token wasn't found or was already revoked -
        // from the client's perspective, "not logged in anymore" is
        // exactly the outcome they wanted either way.
    }

    private AuthResponse issueTokenPair(User user, HttpServletRequest httpRequest) {
        String accessToken = jwtTokenProvider.generateAccessToken(user);

        String rawRefreshToken = generateSecureToken();
        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .user(user)
                .tokenHash(HashUtils.sha256Hex(rawRefreshToken))
                .expiresAt(OffsetDateTime.now().plusDays(refreshTokenTtlDays))
                .revoked(false)
                .userAgent(truncate(httpRequest.getHeader("User-Agent"), 500))
                .ipAddress(HttpRequestUtils.extractClientIp(httpRequest))
                .build();
        refreshTokenRepository.save(refreshTokenEntity);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresInSeconds(accessTokenTtlMinutes * 60)
                .user(userMapper.toResponse(user))
                .build();
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32]; // 256 bits of entropy
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
