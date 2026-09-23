-- Migration 003: add auth fields to users
--
-- Adds the fields required by contracts/auth-api.yaml:
--   id         -> stable UUID user identity / JWT sub
--   username   -> login username
--   roles      -> CUSTOMER / ADMIN
--   created_on -> user creation timestamp
--
--   (password storage moved to credentials table)

BEGIN;

-- UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
-- Dropping old not null conditions since the request hasn't had these mentioned
ALTER TABLE users
    ALTER COLUMN first_name DROP NOT NULL,
    ALTER COLUMN last_name DROP NOT NULL,
    ALTER COLUMN email DROP NOT NULL,
    ALTER COLUMN phone_no DROP NOT NULL;

-- ============================================================
-- 1. Add contract-required columns
-- ============================================================


ALTER TABLE users
    ADD COLUMN IF NOT EXISTS id UUID,
    ADD COLUMN IF NOT EXISTS username VARCHAR(64),
    ADD COLUMN IF NOT EXISTS created_on TIMESTAMPTZ DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS roles TEXT[] DEFAULT ARRAY['CUSTOMER']::TEXT[];

UPDATE users
SET id = gen_random_uuid()
WHERE id IS NULL;


ALTER TABLE users
    ALTER COLUMN id SET DEFAULT gen_random_uuid();


-- ============================================================
-- 3. Give existing users unique usernames
--
-- Generate from the stable UUID rather than account_id.
-- This avoids collisions if multiple users belong to
-- the same trading account.
-- ============================================================

UPDATE users
SET username = 'user-' || id::TEXT
WHERE username IS NULL
   OR username = '';


-- ============================================================
-- 4. Give existing users the default role
-- ============================================================

UPDATE users
SET roles = ARRAY['CUSTOMER']::TEXT[]
WHERE roles IS NULL
   OR array_length(roles, 1) IS NULL
   OR array_length(roles, 1) = 0;


-- ============================================================
-- 5. Ensure timestamps exist
-- ============================================================

UPDATE users
SET created_on = NOW()
WHERE created_on IS NULL;


-- ============================================================
-- 6. Constraints
-- ============================================================

ALTER TABLE users
    ALTER COLUMN id SET NOT NULL,
    ALTER COLUMN username SET NOT NULL,
    ALTER COLUMN roles SET NOT NULL,
    ALTER COLUMN created_on SET NOT NULL;


-- ============================================================
-- 7. Unique identity / username
-- ============================================================

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_id
    ON users(id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_username
    ON users(username);


-- ============================================================
-- 8. Username must obey the contract
-- ============================================================

ALTER TABLE users
    ADD CONSTRAINT chk_users_username_format
    CHECK (
        username ~ '^[a-zA-Z0-9._-]+$'
    );


-- ============================================================
-- 9. Roles must contain at least one valid role
-- ============================================================

ALTER TABLE users
    ADD CONSTRAINT chk_users_roles_non_empty
    CHECK (
        array_length(roles, 1) >= 1
    );


ALTER TABLE users
    ADD CONSTRAINT chk_users_roles_valid
    CHECK (
        roles <@ ARRAY['CUSTOMER', 'ADMIN']::TEXT[]
    );


-- ============================================================
-- 10. Credentials table (merged from 004_create_credentials_table_and_seed.sql)
-- ============================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    username VARCHAR(255) NOT NULL,
    password_hash TEXT NOT NULL,
    algorithm VARCHAR(64) NOT NULL,
    params JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

CREATE UNIQUE INDEX IF NOT EXISTS credentials_account_username_idx ON credentials (account_id, username);

COMMIT;


-- to add value into accounts for successful registration
INSERT INTO accounts (
    account_reference,
    cash_balance,
    buying_power,
    status
)
VALUES (
    'AUTH-TEST-001',
    10000.00,
    10000.00,
    'ACTIVE'
)
RETURNING *;