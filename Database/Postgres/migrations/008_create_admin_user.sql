BEGIN;

-- =============================================================================
-- Enterprise Trading Platform
-- Migration 008: Seed Admin user and account
-- PostgreSQL
-- =============================================================================

-- 1. Create account for admin user
INSERT INTO tradingsystem.accounts (
    account_reference,
    cash_balance,
    buying_power,
    status
) VALUES (
    'ETP-ADMIN-01',
    0.00,
    0.00,
    'ACTIVE'
)
ON CONFLICT (account_reference) DO NOTHING;

-- 2. Create Admin user
-- Roles: ARRAY['ADMIN'] satisfies constraint check (roles <@ ARRAY['CUSTOMER', 'ADMIN']::TEXT[])
INSERT INTO tradingsystem.users (
    account_id,
    username,
    first_name,
    last_name,
    email,
    phone_no,
    roles
)
SELECT
    a.id,
    'Admin123',
    'Platform',
    'Admin',
    'admin@tradingsystem.internal',
    '+1-555-0199',
    ARRAY['ADMIN']::TEXT[]
FROM tradingsystem.accounts a
WHERE a.account_reference = 'ETP-ADMIN-01'
ON CONFLICT (username) DO UPDATE
SET roles = ARRAY['ADMIN']::TEXT[];

-- 3. Create Admin credentials with Argon2id hash for password: "password123456"
-- Argon2id parameters: time=3, memory=32768KB, parallelism=1
INSERT INTO authentication.credentials (
    account_id,
    username,
    password_hash,
    params
)
SELECT
    a.id,
    'Admin123',
    '$argon2id$v=19$m=32768,t=3,p=1$5hXUJy4XTClCFr1na12OCg$NDoD38sE1oXMJtBVLQEXI/eQ5s2VbnOUYWpKvq4CFD8',
    '{"time":3,"memory_kb":32768,"parallelism":1}'::JSONB
FROM tradingsystem.accounts a
WHERE a.account_reference = 'ETP-ADMIN-01'
ON CONFLICT (username) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    params = EXCLUDED.params;

COMMIT;
