package com.shortx.repository;

import com.shortx.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // The single hottest query on this repository: every login and every
    // JWT-authenticated request that needs to reload the principal goes
    // through this. Backed by idx_users_email.
    Optional<User> findByEmail(String email);

    // Used at registration time to return a clean 409 Conflict instead of
    // hitting the DB unique constraint and translating a
    // DataIntegrityViolationException after the fact.
    boolean existsByEmail(String email);
}
