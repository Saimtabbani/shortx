package com.shortx.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A registered account. Deliberately does NOT implement Spring Security's
 * UserDetails directly — that would couple a persistence entity to a
 * security-framework interface, forcing this class to carry
 * framework-shaped methods (getAuthorities(), isAccountNonExpired(), ...)
 * that have nothing to do with what a User *is*. Instead,
 * security.UserPrincipal (added in the Security milestone) wraps this
 * entity and adapts it to UserDetails at the boundary where Spring
 * Security actually needs it.
 *
 * <p>Does NOT map short_urls or refresh_tokens as collections here. Both
 * are unidirectional from the "many" side (ShortUrl -> User,
 * RefreshToken -> User) on purpose: a user can own hundreds of thousands
 * of short URLs, and an {@code @OneToMany} collection on User would be a
 * standing invitation to accidentally trigger-load all of them (e.g. via
 * cascade or an unguarded getter) just to authenticate a login. Anything
 * that needs "this user's URLs" queries ShortUrlRepository directly,
 * paginated.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "roles")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class User {

    @Id
    @GeneratedValue
    @UuidGenerator
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // EAGER is intentional here, not an oversight: roles are a tiny set
    // (almost always exactly one row) needed on every single authenticated
    // request to build the Authentication object. Lazy-loading them would
    // just mean an extra round-trip on every request instead of one join
    // fetch at load time. Contrast with the collections deliberately left
    // off this entity above, which are large and only occasionally needed.
    @ManyToMany(fetch = FetchType.EAGER, cascade = CascadeType.MERGE)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    public void addRole(Role role) {
        this.roles.add(role);
    }
}
