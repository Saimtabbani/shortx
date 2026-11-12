package com.shortx.common.constants;

/**
 * Role name constants matching the seeded rows in the `roles` table
 * (V2__create_roles_table.sql). Centralized here so "ROLE_USER" as a
 * literal string appears in exactly one place instead of being retyped
 * (and potentially mistyped) across services, security config, and tests.
 */
public final class RoleConstants {

    public static final String ROLE_USER = "ROLE_USER";
    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private RoleConstants() {
        // Non-instantiable constants holder.
    }
}
