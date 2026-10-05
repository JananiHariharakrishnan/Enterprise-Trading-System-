BEGIN;

-- =============================================================================
-- Enterprise Trading Platform
-- Migration 002: Seed reference data
-- PostgreSQL
-- =============================================================================

-- -----------------------------------------------------------------------------
-- INSTRUMENTS
-- -----------------------------------------------------------------------------

INSERT INTO tradingsystem.instruments (
    symbol,
    name,
    asset_class,
    currency,
    tradable
) VALUES
    ('INFY.NS',      'Infosys Limited',                       'EQUITY',  'INR', TRUE),
    ('RELIANCE.NS',  'Reliance Industries Limited',           'EQUITY',  'INR', TRUE),
    ('TATASTEEL.BO', 'Tata Steel Limited',                   'EQUITY',  'INR', TRUE),
    ('HDFCBANK.NS', 'HDFC Bank Limited',                     'EQUITY',  'INR', TRUE),
    ('AAPL',        'Apple Inc',                             'EQUITY',  'USD', TRUE),
    ('SPY',         'SPDR S&P 500 ETF Trust',                'ETF',     'USD', TRUE),
    ('FX:EURUSD',   'Euro against United States Dollar',     'FX',      'USD', TRUE),
    ('X:BTC-USD',   'Bitcoin against United States Dollar',  'CRYPTO',  'USD', TRUE),
    ('SATYAM.BO',   'Satyam Computer Services Limited',     'EQUITY',  'INR', FALSE);

-- -----------------------------------------------------------------------------
-- ACCOUNTS
-- account_reference is the stable business/customer reference.
-- -----------------------------------------------------------------------------

INSERT INTO tradingsystem.accounts (
    account_reference,
    cash_balance,
    buying_power,
    status
) VALUES
    ('ETP-2201', 480000.00, 480000.00, 'ACTIVE'),
    ('ETP-2202', 1250000.00, 1250000.00, 'ACTIVE'),
    ('ETP-2203', 3200.00, 3200.00, 'ACTIVE'),
    ('ETP-2204', 95000.00, 95000.00, 'ACTIVE'),
    ('ETP-2205', 220000.00, 220000.00, 'SUSPENDED'),
    ('ETP-2206', 610000.00, 610000.00, 'CLOSED'),
    ('ETP-2207', 1000000.00, 1000000.00, 'ACTIVE'),
    ('ETP-2208', 100000.00, 100000.00, 'ACTIVE'),
    ('ETP-2209', 9000000.00, 9000000.00, 'ACTIVE'),
    ('ETP-22010', 900000.00, 900000.00, 'ACTIVE'),
    ('ETP-22011', 500000.00, 500000.00, 'ACTIVE'),
    ('ETP-22012', 750000.00, 750000.00, 'ACTIVE'),
    ('ETP-22013', 1100000.00, 1100000.00, 'ACTIVE'),
    ('ETP-22014', 850000.00, 850000.00, 'ACTIVE'),
    ('ETP-22015', 650000.00, 650000.00, 'ACTIVE'),
    ('ETP-22016', 1200000.00, 1200000.00, 'ACTIVE'),
    ('ETP-22017', 450000.00, 450000.00, 'ACTIVE'),
    ('ETP-22018', 920000.00, 920000.00, 'ACTIVE'),
    ('ETP-22019', 1050000.00, 1050000.00, 'ACTIVE'),
    ('ETP-22020', 800000.00, 800000.00, 'ACTIVE');

-- -----------------------------------------------------------------------------
-- USERS
-- account_id is resolved from account_reference.
-- -----------------------------------------------------------------------------

INSERT INTO tradingsystem.users (
    account_id,
    username,
    first_name,
    last_name,
    email,
    phone_no
)
SELECT
    a.id,
    v.username,
    v.first_name,
    v.last_name,
    v.email,
    v.phone_no
FROM (
    VALUES
        ('ETP-2201', 'Ananya',  'Iyer',       'ananya.iyer@example.com',  '+91-9000000001', 'ananya.iyer'),
        ('ETP-2202', 'Rohit',   'Deshpande', 'rohit.deshpande@example.com', '+91-9000000002', 'rohit.deshpande'),
        ('ETP-2203', 'Meera',   'Krishnan',  'meera.krishnan@example.com', '+91-9000000003', 'meera.krishnan'),
        ('ETP-2204', 'Vikram',  'Nair',      'vikram.nair@example.com', '+91-9000000004', 'vikram.nair'),
        ('ETP-2205', 'Sana',    'Qureshi',   'sana.qureshi@example.com', '+91-9000000005', 'sana.qureshi'),
        ('ETP-2206', 'Joseph',  'Mathew',    'joseph.mathew@example.com', '+91-9000000006', 'joseph.mathew')
) AS v(
    account_reference,
    first_name,
    last_name,
    email,
    phone_no,
    username
)
JOIN tradingsystem.accounts a
    ON a.account_reference = v.account_reference;

-- -----------------------------------------------------------------------------
-- POSITIONS
-- -----------------------------------------------------------------------------

INSERT INTO tradingsystem.positions (
    account_id,
    symbol,
    qty,
    avg_cost,
    version
)
SELECT
    a.id,
    v.symbol,
    v.qty,
    v.avg_cost,
    0
FROM (
    VALUES
        ('ETP-2201', 'SATYAM.BO',    100,  21.50),
        ('ETP-2201', 'INFY.NS',      300, 1585.50),
        ('ETP-2201', 'TATASTEEL.BO', 500,  164.20),
        ('ETP-2202', 'RELIANCE.NS',  250, 1425.00),
        ('ETP-2202', 'AAPL',          60,  232.50),
        ('ETP-2203', 'TATASTEEL.BO',  15,  163.00),
        ('ETP-2204', 'HDFCBANK.NS',   30, 1672.00)
) AS v(account_reference, symbol, qty, avg_cost)
JOIN tradingsystem.accounts a
    ON a.account_reference = v.account_reference;

-- -----------------------------------------------------------------------------
-- ORDERS
-- UUIDs are deterministic seed values.
-- idempotency_key is unique by database constraint.
-- -----------------------------------------------------------------------------

INSERT INTO tradingsystem.orders (
    id,
    account_id,
    symbol,
    side,
    order_type,
    qty,
    price,
    status,
    idempotency_key,
    created_on
)
SELECT
    v.order_id::UUID,
    a.id,
    v.symbol,
    v.side,
    v.order_type,
    v.qty,
    v.price,
    v.status,
    v.idempotency_key,
    v.created_on::TIMESTAMP
FROM (
    VALUES
        ('1f0c8c10-7a41-4c01-9001-000000000001', 'ETP-2201', 'SATYAM.BO',    'BUY',  'LIMIT', 100,  21.50,    'FILLED',    'seed-2201-001', '2026-05-14 10:31:06'),
        ('1f0c8c10-7a41-4c01-9001-000000000002', 'ETP-2201', 'INFY.NS',      'BUY',  'LIMIT', 200, 1580.50,   'FILLED',    'seed-2201-002', '2026-06-08 09:47:12'),
        ('1f0c8c10-7a41-4c01-9001-000000000003', 'ETP-2201', 'INFY.NS',      'BUY',  'LIMIT', 100, 1595.50,   'FILLED',    'seed-2201-003', '2026-06-19 11:03:45'),
        ('1f0c8c10-7a41-4c01-9001-000000000004', 'ETP-2201', 'TATASTEEL.BO', 'BUY',  'LIMIT', 500,  164.20,   'FILLED',    'seed-2201-004', '2026-06-25 10:15:08'),
        ('1f0c8c10-7a41-4c01-9001-000000000005', 'ETP-2201', 'HDFCBANK.NS',  'BUY',  'LIMIT', 40,  1650.00,   'REJECTED',  'seed-2201-005', '2026-07-02 14:22:31'),
        ('1f0c8c10-7a41-4c01-9001-000000000006', 'ETP-2201', 'TATASTEEL.BO', 'SELL', 'LIMIT', 100,  172.00,   'CANCELLED', 'seed-2201-006', '2026-07-14 15:40:57'),
        ('1f0c8c10-7a41-4c01-9001-000000000007', 'ETP-2201', 'RELIANCE.NS',  'BUY',  'LIMIT', 50,  1410.00,   'NEW',       'seed-2201-007', '2026-08-03 09:05:19'),

        ('1f0c8c10-7a41-4c01-9001-000000000008', 'ETP-2202', 'RELIANCE.NS',  'BUY',  'LIMIT', 400, 1425.00,   'FILLED',    'seed-2202-001', '2026-06-11 09:31:04'),
        ('1f0c8c10-7a41-4c01-9001-000000000009', 'ETP-2202', 'AAPL',          'BUY',  'LIMIT', 60,  232.50,   'FILLED',    'seed-2202-002', '2026-06-30 13:52:26'),
        ('1f0c8c10-7a41-4c01-9001-000000000010', 'ETP-2202', 'RELIANCE.NS',  'SELL', 'LIMIT', 150, 1468.00,   'FILLED',    'seed-2202-003', '2026-07-09 10:28:33'),
        ('1f0c8c10-7a41-4c01-9001-000000000011', 'ETP-2202', 'X:BTC-USD',    'BUY',  'LIMIT', 1,  61000.00,   'REJECTED',  'seed-2202-004', '2026-07-21 16:11:02'),
        ('1f0c8c10-7a41-4c01-9001-000000000012', 'ETP-2202', 'SPY',           'BUY',  'LIMIT', 25,  548.00,    'NEW',       'seed-2202-005', '2026-08-04 08:12:47'),

        ('1f0c8c10-7a41-4c01-9001-000000000013', 'ETP-2203', 'TATASTEEL.BO', 'BUY',  'LIMIT', 15,  163.00,    'FILLED',    'seed-2203-001', '2026-06-17 12:05:41'),
        ('1f0c8c10-7a41-4c01-9001-000000000014', 'ETP-2203', 'INFY.NS',      'BUY',  'LIMIT', 10, 1585.00,    'REJECTED',  'seed-2203-002', '2026-07-06 09:58:15'),
        ('1f0c8c10-7a41-4c01-9001-000000000015', 'ETP-2203', 'TATASTEEL.BO', 'BUY',  'LIMIT', 20,  160.00,    'NEW',       'seed-2203-003', '2026-08-05 10:44:29'),

        ('1f0c8c10-7a41-4c01-9001-000000000016', 'ETP-2204', 'HDFCBANK.NS',  'BUY',  'LIMIT', 30, 1672.00,    'FILLED',    'seed-2204-001', '2026-06-23 11:19:53'),
        ('1f0c8c10-7a41-4c01-9001-000000000017', 'ETP-2204', 'SPY',           'BUY',  'LIMIT', 10,  552.00,    'CANCELLED', 'seed-2204-002', '2026-07-28 14:03:38'),
        ('1f0c8c10-7a41-4c01-9001-000000000018', 'ETP-2204', 'AAPL',          'BUY',  'LIMIT', 20,  230.00,    'NEW',       'seed-2204-003', '2026-08-06 09:22:10')
) AS v(
    order_id,
    account_reference,
    symbol,
    side,
    order_type,
    qty,
    price,
    status,
    idempotency_key,
    created_on
)
JOIN tradingsystem.accounts a
    ON a.account_reference = v.account_reference;

-- -----------------------------------------------------------------------------
-- EXECUTIONS
-- Only FILLED orders receive executions.
-- -----------------------------------------------------------------------------

INSERT INTO tradingsystem.executions (
    order_id,
    quantity,
    price,
    executed_at,
    created_at,
    event_id
)
SELECT
    o.id,
    v.quantity,
    v.price,
    v.executed_at::TIMESTAMP,
    v.executed_at::TIMESTAMP,
    v.event_id::UUID
FROM (
    VALUES
        ('1f0c8c10-7a41-4c01-9001-000000000001', 100,  21.50,  '2026-05-14 10:31:20', 'f0c8c100-0000-0001-0000-000000000001'),
        ('1f0c8c10-7a41-4c01-9001-000000000002', 200, 1580.50, '2026-06-08 09:47:30', 'f0c8c100-0000-0002-0000-000000000002'),
        ('1f0c8c10-7a41-4c01-9001-000000000003', 100, 1595.50, '2026-06-19 11:04:01', 'f0c8c100-0000-0003-0000-000000000003'),
        ('1f0c8c10-7a41-4c01-9001-000000000004', 500,  164.20, '2026-06-25 10:15:22', 'f0c8c100-0000-0004-0000-000000000004'),
        ('1f0c8c10-7a41-4c01-9001-000000000008', 400, 1425.00, '2026-06-11 09:31:25', 'f0c8c100-0000-0008-0000-000000000008'),
        ('1f0c8c10-7a41-4c01-9001-000000000009', 60,   232.50, '2026-06-30 13:52:45', 'f0c8c100-0000-0009-0000-000000000009'),
        ('1f0c8c10-7a41-4c01-9001-000000000010', 150, 1468.00, '2026-07-09 10:28:50', 'f0c8c100-0000-0010-0000-000000000010'),
        ('1f0c8c10-7a41-4c01-9001-000000000013', 15,   163.00, '2026-06-17 12:06:02', 'f0c8c100-0000-0013-0000-000000000013'),
        ('1f0c8c10-7a41-4c01-9001-000000000016', 30,  1672.00, '2026-06-23 11:20:10', 'f0c8c100-0000-0016-0000-000000000016')
) AS v(order_id, quantity, price, executed_at, event_id)
JOIN tradingsystem.orders o
    ON o.id = v.order_id::UUID;

-- =============================================================================
-- E2E TEST ACCOUNT
-- Used by all developers for e2e testing. Credentials:
-- Username: e2e.tester
-- Password: correct horse battery staple
-- =============================================================================

-- Insert the E2E test account
INSERT INTO tradingsystem.accounts (
    account_reference,
    cash_balance,
    buying_power,
    status
) VALUES
    ('E2E-TEST', 1000000.00, 1000000.00, 'ACTIVE')
ON CONFLICT (account_reference) DO NOTHING;

-- Insert the E2E test user
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
    'e2e.tester',
    'E2E',
    'Tester',
    'e2e.tester@example.com',
    '+91-9999999999',
    ARRAY['CUSTOMER']::TEXT[]
FROM tradingsystem.accounts a
WHERE a.account_reference = 'E2E-TEST'
ON CONFLICT (username) DO NOTHING;

-- Insert the credentials with Argon2id hashed password
-- Hash generated for password: "correct horse battery staple"
-- Argon2id parameters: time=3, memory=32768KB, parallelism=1
INSERT INTO authentication.credentials (
    account_id,
    username,
    password_hash,
    params
)
SELECT
    a.id,
    'e2e.tester',
    '$argon2id$v=19$m=32768,t=3,p=1$YbHCCX7EM4e9aYX3NKyXJA$YiAmEiAG+qAVrHUo3rlM02sdavfGYxaKrF8FSF0gzNs',
    '{"time":3,"memory_kb":32768,"parallelism":1}'::JSONB
FROM tradingsystem.accounts a
WHERE a.account_reference = 'E2E-TEST'
ON CONFLICT (account_id) DO NOTHING;

COMMIT;