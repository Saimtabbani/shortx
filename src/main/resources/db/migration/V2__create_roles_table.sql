-- Small, near-static reference table (a handful of rows, ever). INTEGER PK
-- (matches the Role entity's Integer id so Hibernate schema-validation is
-- satisfied); there is no scalability concern here since this table is never
-- written to at request time — only at deploy time when a new role appears.
CREATE TABLE roles (
    id          INTEGER PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_roles_name UNIQUE (name)
);

COMMENT ON TABLE roles IS 'Static set of authorization roles. Seeded at migration time; not user-editable at runtime.';
COMMENT ON COLUMN roles.name IS 'Role identifier used in JWT authorities, e.g. ROLE_USER, ROLE_ADMIN. Prefixed to match Spring Security convention (hasRole strips the prefix automatically).';

INSERT INTO roles (id, name, description) VALUES
    (1, 'ROLE_USER',  'Standard authenticated user: can create/manage their own short URLs.'),
    (2, 'ROLE_ADMIN', 'Administrative user: can manage any user''s URLs and view system-wide analytics.');
