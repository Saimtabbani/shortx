CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id         UUID NOT NULL,

    -- We store a SHA-256 hash of the refresh token, never the raw token.
    -- Rationale: a refresh token is a long-lived bearer credential — if the
    -- database were ever compromised (backup leak, SQL injection, etc.),
    -- storing raw tokens would let an attacker impersonate every user for
    -- up to refresh-token-ttl-days. Hashing means a DB leak alone is not
    -- enough to forge sessions.
    token_hash      VARCHAR(64) NOT NULL,

    expires_at      TIMESTAMPTZ NOT NULL,

    -- Supports refresh-token rotation: when a refresh token is used, it is
    -- marked revoked and a new one is issued. If a revoked token is ever
    -- presented again, that's a signal of token theft/replay and the
    -- entire token family (or user session) can be invalidated.
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,

    -- Denormalized request metadata for security auditing / "log out other
    -- devices" UX — lets a user see "Chrome on macOS, last used from
    -- 203.0.113.4" without joining against a separate session-log table.
    user_agent      VARCHAR(500),
    ip_address      VARCHAR(45), -- long enough for IPv6

    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

-- Refresh flow does `WHERE token_hash = ? AND revoked = false AND
-- expires_at > now()`. The unique constraint above covers the equality
-- lookup; this composite index covers "list a user's active sessions".
CREATE INDEX idx_refresh_tokens_user_active ON refresh_tokens (user_id, revoked, expires_at);

COMMENT ON TABLE refresh_tokens IS 'Server-side refresh token registry enabling rotation, per-device revocation, and "log out all devices". Access tokens (JWTs) are NOT stored here — they are stateless and validated by signature alone.';
