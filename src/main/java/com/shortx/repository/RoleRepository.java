package com.shortx.repository;

import com.shortx.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Read-mostly access to the static roles table. No custom write methods —
 * roles are seeded by Flyway (V2 migration), not created through the
 * application.
 */
public interface RoleRepository extends JpaRepository<Role, Integer> {

    Optional<Role> findByName(String name);
}
