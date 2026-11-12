package com.shortx.security;

import com.shortx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads a user by email for {@code DaoAuthenticationProvider} to verify
 * credentials against during login. This is the ONLY place in the
 * application this class is used — every authenticated request after
 * login is validated via JWT signature/claims (JwtAuthenticationFilter),
 * not by calling back into this service.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(normalize(email))
                .map(UserPrincipal::new)
                // Spring Security's DaoAuthenticationProvider converts this
                // into the same generic BadCredentialsException a wrong
                // password would produce (hideUserNotFoundExceptions
                // defaults to true), so "no such user" and "wrong
                // password" are indistinguishable to the caller.
                .orElseThrow(() -> new UsernameNotFoundException("No user found with email: " + email));
    }

    private String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
