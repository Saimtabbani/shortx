CREATE TABLE users (
    -- UUID, not a sequential ID: user IDs appear in JWTs and API responses.
    -- A sequential ID would let anyone enumerate users (/api/v1/users/1,
    -- /2, /3...) or infer signup volume/growth rate from ID gaps.
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Login identifier. Citext would auto-lowercase, but that adds an
    -- extension dependency for one column; instead we normalize to
    -- lowercase in the application layer before every read/write and
    -- enforce it here too so the constraint holds even if the app layer
    -- is ever bypassed (manual SQL, another service, etc).
    email           VARCHAR(255) NOT NULL,

    -- BCrypt hash only. The raw password is never persisted, logged, or
    -- passed to any layer beyond the auth service. 255 chars comfortably
    -- fits BCrypt's 60-char output with room for future algorithm changes
    -- (e.g. migrating to Argon2id) without a column resize.
    password_hash   VARCHAR(255) NOT NULL,

    full_name       VARCHAR(255) NOT NULL,

    -- Soft account-disable switch (e.g. admin suspends an abusive account)
    -- distinct from deletion — preserves the user's URLs and audit trail.
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- Every login does `WHERE email = ?` — this is the single hottest query
-- against this table, so it gets an index even though the UNIQUE
-- constraint above already implies one (documenting the intent explicitly
-- rather than relying on an implicit side effect).
CREATE INDEX idx_users_email ON users (email);

COMMENT ON COLUMN users.enabled IS 'Soft-disable flag checked by Spring Security UserDetailsService (isEnabled). Distinct from deletion so a suspended user''s URLs remain intact and auditable.';

-- Join table for the users<->roles many-to-many relationship. Most users
-- will have exactly one role, but modeling it as a join table (rather than
-- a single role_id column on users) avoids a future migration if
-- multi-role users are ever needed, at negligible cost today.
CREATE TABLE user_roles (
    user_id     UUID NOT NULL,
    role_id     INTEGER NOT NULL,

    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE RESTRICT
);

COMMENT ON CONSTRAINT fk_user_roles_role ON user_roles IS 'ON DELETE RESTRICT: a role in active use can''t be deleted out from under assigned users; roles must be reassigned first.';
COMMENT ON CONSTRAINT fk_user_roles_user ON user_roles IS 'ON DELETE CASCADE: deleting a user cleans up their role assignments automatically.';
