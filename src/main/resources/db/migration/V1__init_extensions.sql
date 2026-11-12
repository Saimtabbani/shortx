-- pgcrypto gives us gen_random_uuid() for security-sensitive tables
-- (users, refresh_tokens) where IDs must not be sequential/guessable.
-- High-volume tables (short_urls, click_events) deliberately do NOT use
-- UUIDs — see V6/V7 for the Snowflake-ID rationale.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
