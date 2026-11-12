package com.shortx.security;

import com.shortx.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adapts a {@link User} entity to Spring Security's {@code UserDetails}
 * contract at the one boundary that actually needs it: credential
 * verification during login (see CustomUserDetailsService /
 * DaoAuthenticationProvider). This is deliberately NOT how identity is
 * established on every subsequent authenticated request — per-request
 * auth is handled entirely by JwtAuthenticationFilter reading claims
 * already embedded in the JWT, with no second database lookup and no
 * second pass through this class. Keeping the entity itself free of this
 * interface (see User's own Javadoc) means this coupling lives in exactly
 * one small adapter class instead of spreading through the entity.
 */
public class UserPrincipal implements UserDetails {

    private final User user;

    public UserPrincipal(User user) {
        this.user = user;
    }

    public UUID getId() {
        return user.getId();
    }

    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName()))
                .collect(Collectors.toList());
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        // "Username" in the UserDetailsService sense only — this is the
        // login identifier (email), NOT what ends up as the JWT subject
        // or as Authentication.getName() on later requests (that's the
        // user's UUID; see JwtTokenProvider/JwtAuthenticationFilter).
        return user.getEmail();
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
}
