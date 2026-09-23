-- Migration 004: add unique index on credentials.username
-- This creates a global unique index so usernames are unique across accounts

BEGIN;

CREATE UNIQUE INDEX IF NOT EXISTS uq_credentials_username
    ON credentials(username);

COMMIT;
