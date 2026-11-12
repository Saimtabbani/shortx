package com.shortx.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.UUID;

/**
 * Extracts identity/role information from Spring Security's
 * {@link Authentication}, without requiring callers to know or depend on
 * the concrete principal type.
 *
 * <p>Deliberately built against the generic {@code Authentication}
 * contract rather than a custom {@code UserPrincipal} class: the JWT
 * authentication filter (added in the Security milestone) sets the
 * authenticated user's UUID as the token subject and, by convention, as
 * {@link Authentication#getName()}. That means this class — and every
 * controller that uses it — works correctly once the Security milestone
 * lands, with zero changes needed here. Written now, ahead of the
 * Security milestone, specifically so the REST API controllers don't sit
 * blocked waiting on JWT infrastructure that has no bearing on their own
 * request/response logic.
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // Non-instantiable utility class.
    }

    /**
     * Returns the authenticated user's ID, or {@code null} if the request
     * is unauthenticated/anonymous. Endpoints that support anonymous
     * access (e.g. anonymous URL creation) treat a null return as "no
     * owner" rather than rejecting the request.
     */
    public static UUID currentUserId(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static boolean isAdmin(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(com.shortx.common.constants.RoleConstants.ROLE_ADMIN::equals);
    }

    private static boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
