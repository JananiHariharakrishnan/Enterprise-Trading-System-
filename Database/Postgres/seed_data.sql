-- =============================================================================
-- Enterprise Trading Platform - seed data
-- Built from migrations/ 002, 005, 008 and the data part of 012, in filename
-- order. Run after schema.sql, inside one transaction:
--   psql -v ON_ERROR_STOP=1 --single-transaction -f seed_data.sql
-- =============================================================================

-- migration 005 uses unqualified table names; set the search_path it relied on
-- (migration 001 set it for its own session).
SET search_path TO tradingsystem, authentication, public;

-- ===== from migrations/002_load_seed_data.sql =====
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
    ('ETP-2201', 10000.00, 10000.00, 'ACTIVE'),
    ('ETP-2202', 10000.00, 10000.00, 'ACTIVE'),
    ('ETP-2203', 10000.00, 10000.00, 'ACTIVE'),
    ('ETP-2204', 10000.00, 10000.00, 'ACTIVE'),
    ('ETP-2205', 10000.00, 10000.00, 'SUSPENDED'),
    ('ETP-2206', 10000.00, 10000.00, 'CLOSED'),
    ('ETP-2207', 10000.00, 10000.00, 'ACTIVE'),
    ('ETP-2208', 10000.00, 10000.00, 'ACTIVE'),
    ('ETP-2209', 10000.00, 10000.00, 'ACTIVE'),
    ('ETP-22010', 10000.00, 10000.00, 'ACTIVE'),
    ('ETP-22011', 10000.00, 10000.00, 'ACTIVE'),
    ('ETP-22012', 10000.00, 10000.00, 'ACTIVE'),
    ('ETP-22013', 10000.00, 10000.00, 'ACTIVE'),
    ('ETP-22014', 10000.00, 10000.00, 'ACTIVE'),
    ('ETP-22015', 10000.00, 10000.00, 'ACTIVE'),
    ('ETP-22016', 10000.00, 10000.00, 'ACTIVE'),
    ('ETP-22017', 10000.00, 10000.00, 'ACTIVE'),
    ('ETP-22018', 10000.00, 10000.00, 'ACTIVE'),
    ('ETP-22019', 10000.00, 10000.00, 'ACTIVE'),
    ('ETP-22020', 10000.00, 10000.00, 'ACTIVE');

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
    ('E2E-TEST', 10000.00, 10000.00, 'ACTIVE')
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


-- ===== from migrations/005_instruments.sql =====
INSERT INTO instruments (symbol, name, asset_class, currency, tradable)
VALUES

-- =========================================================
-- 🇺🇸 UNITED STATES — 150 EQUITIES
-- =========================================================

('MSFT', 'Microsoft Corporation', 'EQUITY', 'USD', true),
('GOOGL', 'Alphabet Inc.', 'EQUITY', 'USD', true),
('GOOG', 'Alphabet Inc.', 'EQUITY', 'USD', true),
('AMZN', 'Amazon.com Inc.', 'EQUITY', 'USD', true),
('NVDA', 'NVIDIA Corporation', 'EQUITY', 'USD', true),
('META', 'Meta Platforms Inc.', 'EQUITY', 'USD', true),
('TSLA', 'Tesla Inc.', 'EQUITY', 'USD', true),
('AVGO', 'Broadcom Inc.', 'EQUITY', 'USD', true),
('ORCL', 'Oracle Corporation', 'EQUITY', 'USD', true),
('BRK.B', 'Berkshire Hathaway Inc.', 'EQUITY', 'USD', true),
('JPM', 'JPMorgan Chase & Co.', 'EQUITY', 'USD', true),
('V', 'Visa Inc.', 'EQUITY', 'USD', true),
('MA', 'Mastercard Incorporated', 'EQUITY', 'USD', true),
('LLY', 'Eli Lilly and Company', 'EQUITY', 'USD', true),
('WMT', 'Walmart Inc.', 'EQUITY', 'USD', true),
('XOM', 'Exxon Mobil Corporation', 'EQUITY', 'USD', true),
('JNJ', 'Johnson & Johnson', 'EQUITY', 'USD', true),
('COST', 'Costco Wholesale Corporation', 'EQUITY', 'USD', true),
('NFLX', 'Netflix Inc.', 'EQUITY', 'USD', true),
('AMD', 'Advanced Micro Devices Inc.', 'EQUITY', 'USD', true),
('CRM', 'Salesforce Inc.', 'EQUITY', 'USD', true),
('PLTR', 'Palantir Technologies Inc.', 'EQUITY', 'USD', true),
('HD', 'The Home Depot Inc.', 'EQUITY', 'USD', true),
('PG', 'Procter & Gamble Company', 'EQUITY', 'USD', true),
('ABBV', 'AbbVie Inc.', 'EQUITY', 'USD', true),
('BAC', 'Bank of America Corporation', 'EQUITY', 'USD', true),
('KO', 'The Coca-Cola Company', 'EQUITY', 'USD', true),
('CSCO', 'Cisco Systems Inc.', 'EQUITY', 'USD', true),
('CVX', 'Chevron Corporation', 'EQUITY', 'USD', true),
('MRK', 'Merck & Co. Inc.', 'EQUITY', 'USD', true),
('IBM', 'International Business Machines Corporation', 'EQUITY', 'USD', true),
('MCD', 'McDonald''s Corporation', 'EQUITY', 'USD', true),
('ACN', 'Accenture plc', 'EQUITY', 'USD', true),
('LIN', 'Linde plc', 'EQUITY', 'USD', true),
('GE', 'GE Aerospace', 'EQUITY', 'USD', true),
('ABT', 'Abbott Laboratories', 'EQUITY', 'USD', true),
('WFC', 'Wells Fargo & Company', 'EQUITY', 'USD', true),
('INTU', 'Intuit Inc.', 'EQUITY', 'USD', true),
('PM', 'Philip Morris International Inc.', 'EQUITY', 'USD', true),
('TMO', 'Thermo Fisher Scientific Inc.', 'EQUITY', 'USD', true),
('ISRG', 'Intuitive Surgical Inc.', 'EQUITY', 'USD', true),
('TXN', 'Texas Instruments Incorporated', 'EQUITY', 'USD', true),
('DIS', 'The Walt Disney Company', 'EQUITY', 'USD', true),
('CAT', 'Caterpillar Inc.', 'EQUITY', 'USD', true),
('AMAT', 'Applied Materials Inc.', 'EQUITY', 'USD', true),
('QCOM', 'QUALCOMM Incorporated', 'EQUITY', 'USD', true),
('VZ', 'Verizon Communications Inc.', 'EQUITY', 'USD', true),
('RTX', 'RTX Corporation', 'EQUITY', 'USD', true),
('GS', 'The Goldman Sachs Group Inc.', 'EQUITY', 'USD', true),
('AXP', 'American Express Company', 'EQUITY', 'USD', true),
('PFE', 'Pfizer Inc.', 'EQUITY', 'USD', true),
('UBER', 'Uber Technologies Inc.', 'EQUITY', 'USD', true),
('NOW', 'ServiceNow Inc.', 'EQUITY', 'USD', true),
('SPGI', 'S&P Global Inc.', 'EQUITY', 'USD', true),
('BKNG', 'Booking Holdings Inc.', 'EQUITY', 'USD', true),
('ADBE', 'Adobe Inc.', 'EQUITY', 'USD', true),
('NEE', 'NextEra Energy Inc.', 'EQUITY', 'USD', true),
('LOW', 'Lowe''s Companies Inc.', 'EQUITY', 'USD', true),
('T', 'AT&T Inc.', 'EQUITY', 'USD', true),
('SYK', 'Stryker Corporation', 'EQUITY', 'USD', true),
('BLK', 'BlackRock Inc.', 'EQUITY', 'USD', true),
('TJX', 'TJX Companies Inc.', 'EQUITY', 'USD', true),
('DE', 'Deere & Company', 'EQUITY', 'USD', true),
('GILD', 'Gilead Sciences Inc.', 'EQUITY', 'USD', true),
('VRTX', 'Vertex Pharmaceuticals Incorporated', 'EQUITY', 'USD', true),
('COP', 'ConocoPhillips', 'EQUITY', 'USD', true),
('ADP', 'Automatic Data Processing Inc.', 'EQUITY', 'USD', true),
('LMT', 'Lockheed Martin Corporation', 'EQUITY', 'USD', true),
('MMC', 'Marsh & McLennan Companies Inc.', 'EQUITY', 'USD', true),
('SCHW', 'Charles Schwab Corporation', 'EQUITY', 'USD', true),
('MDT', 'Medtronic plc', 'EQUITY', 'USD', true),
('C', 'Citigroup Inc.', 'EQUITY', 'USD', true),
('BMY', 'Bristol-Myers Squibb Company', 'EQUITY', 'USD', true),
('CVS', 'CVS Health Corporation', 'EQUITY', 'USD', true),
('AMGN', 'Amgen Inc.', 'EQUITY', 'USD', true),
('MO', 'Altria Group Inc.', 'EQUITY', 'USD', true),
('SO', 'Southern Company', 'EQUITY', 'USD', true),
('PLD', 'Prologis Inc.', 'EQUITY', 'USD', true),
('CB', 'Chubb Limited', 'EQUITY', 'USD', true),
('CI', 'The Cigna Group', 'EQUITY', 'USD', true),
('DUK', 'Duke Energy Corporation', 'EQUITY', 'USD', true),
('BSX', 'Boston Scientific Corporation', 'EQUITY', 'USD', true),
('ICE', 'Intercontinental Exchange Inc.', 'EQUITY', 'USD', true),
('REGN', 'Regeneron Pharmaceuticals Inc.', 'EQUITY', 'USD', true),
('ETN', 'Eaton Corporation plc', 'EQUITY', 'USD', true),
('ZTS', 'Zoetis Inc.', 'EQUITY', 'USD', true),
('BDX', 'Becton Dickinson and Company', 'EQUITY', 'USD', true),
('NKE', 'Nike Inc.', 'EQUITY', 'USD', true),
('EL', 'Estee Lauder Companies Inc.', 'EQUITY', 'USD', true),
('ITW', 'Illinois Tool Works Inc.', 'EQUITY', 'USD', true),
('MMM', '3M Company', 'EQUITY', 'USD', true),
('USB', 'U.S. Bancorp', 'EQUITY', 'USD', true),
('PNC', 'PNC Financial Services Group Inc.', 'EQUITY', 'USD', true),
('FDX', 'FedEx Corporation', 'EQUITY', 'USD', true),
('UPS', 'United Parcel Service Inc.', 'EQUITY', 'USD', true),
('GM', 'General Motors Company', 'EQUITY', 'USD', true),
('F', 'Ford Motor Company', 'EQUITY', 'USD', true),
('TGT', 'Target Corporation', 'EQUITY', 'USD', true),
('SBUX', 'Starbucks Corporation', 'EQUITY', 'USD', true),
('CMCSA', 'Comcast Corporation', 'EQUITY', 'USD', true),
('CSX', 'CSX Corporation', 'EQUITY', 'USD', true),
('NSC', 'Norfolk Southern Corporation', 'EQUITY', 'USD', true),
('APD', 'Air Products and Chemicals Inc.', 'EQUITY', 'USD', true),
('EMR', 'Emerson Electric Co.', 'EQUITY', 'USD', true),
('EOG', 'EOG Resources Inc.', 'EQUITY', 'USD', true),
('SLB', 'SLB', 'EQUITY', 'USD', true),
('PSA', 'Public Storage', 'EQUITY', 'USD', true),
('OXY', 'Occidental Petroleum Corporation', 'EQUITY', 'USD', true),
('MPC', 'Marathon Petroleum Corporation', 'EQUITY', 'USD', true),
('PSX', 'Phillips 66', 'EQUITY', 'USD', true),
('VLO', 'Valero Energy Corporation', 'EQUITY', 'USD', true),
('HUM', 'Humana Inc.', 'EQUITY', 'USD', true),
('AON', 'Aon plc', 'EQUITY', 'USD', true),
('MET', 'MetLife Inc.', 'EQUITY', 'USD', true),
('PRU', 'Prudential Financial Inc.', 'EQUITY', 'USD', true),
('ALL', 'Allstate Corporation', 'EQUITY', 'USD', true),
('TRV', 'Travelers Companies Inc.', 'EQUITY', 'USD', true),
('COF', 'Capital One Financial Corporation', 'EQUITY', 'USD', true),
('AIG', 'American International Group Inc.', 'EQUITY', 'USD', true),
('BK', 'Bank of New York Mellon Corporation', 'EQUITY', 'USD', true),
('STT', 'State Street Corporation', 'EQUITY', 'USD', true),
('TROW', 'T. Rowe Price Group Inc.', 'EQUITY', 'USD', true),
('FIS', 'Fidelity National Information Services Inc.', 'EQUITY', 'USD', true),
('FISV', 'Fiserv Inc.', 'EQUITY', 'USD', true),
('PYPL', 'PayPal Holdings Inc.', 'EQUITY', 'USD', true),
('SQ', 'Block Inc.', 'EQUITY', 'USD', true),
('MU', 'Micron Technology Inc.', 'EQUITY', 'USD', true),
('INTC', 'Intel Corporation', 'EQUITY', 'USD', true),
('MRVL', 'Marvell Technology Inc.', 'EQUITY', 'USD', true),
('LRCX', 'Lam Research Corporation', 'EQUITY', 'USD', true),
('KLAC', 'KLA Corporation', 'EQUITY', 'USD', true),
('SNPS', 'Synopsys Inc.', 'EQUITY', 'USD', true),
('CDNS', 'Cadence Design Systems Inc.', 'EQUITY', 'USD', true),
('ADI', 'Analog Devices Inc.', 'EQUITY', 'USD', true),
('NXPI', 'NXP Semiconductors N.V.', 'EQUITY', 'USD', true),
('PANW', 'Palo Alto Networks Inc.', 'EQUITY', 'USD', true),
('CRWD', 'CrowdStrike Holdings Inc.', 'EQUITY', 'USD', true),
('SHOP', 'Shopify Inc.', 'EQUITY', 'USD', true),
('COIN', 'Coinbase Global Inc.', 'EQUITY', 'USD', true),
('ABNB', 'Airbnb Inc.', 'EQUITY', 'USD', true),
('DASH', 'DoorDash Inc.', 'EQUITY', 'USD', true),
('SNOW', 'Snowflake Inc.', 'EQUITY', 'USD', true),
('DDOG', 'Datadog Inc.', 'EQUITY', 'USD', true),
('CSGP', 'CoStar Group Inc.', 'EQUITY', 'USD', true),
('MELI', 'MercadoLibre Inc.', 'EQUITY', 'USD', true),
('VEEV', 'Veeva Systems Inc.', 'EQUITY', 'USD', true),
('VRTX', 'Vertex Pharmaceuticals Incorporated', 'EQUITY', 'USD', true),

-- =========================================================
-- 🇮🇳 INDIA — 150 EQUITIES
-- =========================================================

('TCS.NS', 'Tata Consultancy Services Limited', 'EQUITY', 'INR', true),
('ICICIBANK.NS', 'ICICI Bank Limited', 'EQUITY', 'INR', true),
('SBIN.NS', 'State Bank of India', 'EQUITY', 'INR', true),
('BHARTIARTL.NS', 'Bharti Airtel Limited', 'EQUITY', 'INR', true),
('ITC.NS', 'ITC Limited', 'EQUITY', 'INR', true),
('LT.NS', 'Larsen & Toubro Limited', 'EQUITY', 'INR', true),
('HINDUNILVR.NS', 'Hindustan Unilever Limited', 'EQUITY', 'INR', true),
('BAJFINANCE.NS', 'Bajaj Finance Limited', 'EQUITY', 'INR', true),
('MARUTI.NS', 'Maruti Suzuki India Limited', 'EQUITY', 'INR', true),
('KOTAKBANK.NS', 'Kotak Mahindra Bank Limited', 'EQUITY', 'INR', true),
('AXISBANK.NS', 'Axis Bank Limited', 'EQUITY', 'INR', true),
('SUNPHARMA.NS', 'Sun Pharmaceutical Industries Limited', 'EQUITY', 'INR', true),
('HCLTECH.NS', 'HCL Technologies Limited', 'EQUITY', 'INR', true),
('M&M.NS', 'Mahindra & Mahindra Limited', 'EQUITY', 'INR', true),
('TITAN.NS', 'Titan Company Limited', 'EQUITY', 'INR', true),
('ULTRACEMCO.NS', 'UltraTech Cement Limited', 'EQUITY', 'INR', true),
('ADANIENT.NS', 'Adani Enterprises Limited', 'EQUITY', 'INR', true),
('ADANIPORTS.NS', 'Adani Ports and SEZ Limited', 'EQUITY', 'INR', true),
('NTPC.NS', 'NTPC Limited', 'EQUITY', 'INR', true),
('ONGC.NS', 'Oil & Natural Gas Corporation Limited', 'EQUITY', 'INR', true),
('POWERGRID.NS', 'Power Grid Corporation of India Limited', 'EQUITY', 'INR', true),
('WIPRO.NS', 'Wipro Limited', 'EQUITY', 'INR', true),
('NESTLEIND.NS', 'Nestle India Limited', 'EQUITY', 'INR', true),
('ASIANPAINT.NS', 'Asian Paints Limited', 'EQUITY', 'INR', true),
('TATAMOTORS.NS', 'Tata Motors Limited', 'EQUITY', 'INR', true),
('TATACONSUM.NS', 'Tata Consumer Products Limited', 'EQUITY', 'INR', true),
('TECHM.NS', 'Tech Mahindra Limited', 'EQUITY', 'INR', true),
('BAJAJFINSV.NS', 'Bajaj Finserv Limited', 'EQUITY', 'INR', true),
('COALINDIA.NS', 'Coal India Limited', 'EQUITY', 'INR', true),
('JSWSTEEL.NS', 'JSW Steel Limited', 'EQUITY', 'INR', true),
('HINDALCO.NS', 'Hindalco Industries Limited', 'EQUITY', 'INR', true),
('GRASIM.NS', 'Grasim Industries Limited', 'EQUITY', 'INR', true),
('EICHERMOT.NS', 'Eicher Motors Limited', 'EQUITY', 'INR', true),
('CIPLA.NS', 'Cipla Limited', 'EQUITY', 'INR', true),
('DRREDDY.NS', 'Dr. Reddy''s Laboratories Limited', 'EQUITY', 'INR', true),
('DIVISLAB.NS', 'Divi''s Laboratories Limited', 'EQUITY', 'INR', true),
('APOLLOHOSP.NS', 'Apollo Hospitals Enterprise Limited', 'EQUITY', 'INR', true),
('BRITANNIA.NS', 'Britannia Industries Limited', 'EQUITY', 'INR', true),
('HEROMOTOCO.NS', 'Hero MotoCorp Limited', 'EQUITY', 'INR', true),
('BAJAJ-AUTO.NS', 'Bajaj Auto Limited', 'EQUITY', 'INR', true),
('SHRIRAMFIN.NS', 'Shriram Finance Limited', 'EQUITY', 'INR', true),
('BEL.NS', 'Bharat Electronics Limited', 'EQUITY', 'INR', true),
('TRENT.NS', 'Trent Limited', 'EQUITY', 'INR', true),
('TATAPOWER.NS', 'Tata Power Company Limited', 'EQUITY', 'INR', true),
('IOC.NS', 'Indian Oil Corporation Limited', 'EQUITY', 'INR', true),
('BPCL.NS', 'Bharat Petroleum Corporation Limited', 'EQUITY', 'INR', true),
('INDUSINDBK.NS', 'IndusInd Bank Limited', 'EQUITY', 'INR', true),
('HDFCLIFE.NS', 'HDFC Life Insurance Company Limited', 'EQUITY', 'INR', true),
('SBILIFE.NS', 'SBI Life Insurance Company Limited', 'EQUITY', 'INR', true),
('ICICIPRULI.NS', 'ICICI Prudential Life Insurance Company Limited', 'EQUITY', 'INR', true),
('ICICIGI.NS', 'ICICI Lombard General Insurance Company Limited', 'EQUITY', 'INR', true),
('DABUR.NS', 'Dabur India Limited', 'EQUITY', 'INR', true),
('GODREJCP.NS', 'Godrej Consumer Products Limited', 'EQUITY', 'INR', true),
('PIDILITIND.NS', 'Pidilite Industries Limited', 'EQUITY', 'INR', true),
('SIEMENS.NS', 'Siemens Limited', 'EQUITY', 'INR', true),
('ABB.NS', 'ABB India Limited', 'EQUITY', 'INR', true),
('HAVELLS.NS', 'Havells India Limited', 'EQUITY', 'INR', true),
('VOLTAS.NS', 'Voltas Limited', 'EQUITY', 'INR', true),
('DIXON.NS', 'Dixon Technologies India Limited', 'EQUITY', 'INR', true),
('HAL.NS', 'Hindustan Aeronautics Limited', 'EQUITY', 'INR', true),
('BHEL.NS', 'Bharat Heavy Electricals Limited', 'EQUITY', 'INR', true),
('RVNL.NS', 'Rail Vikas Nigam Limited', 'EQUITY', 'INR', true),
('IRCTC.NS', 'Indian Railway Catering and Tourism Corporation Limited', 'EQUITY', 'INR', true),
('IRFC.NS', 'Indian Railway Finance Corporation Limited', 'EQUITY', 'INR', true),
('RECLTD.NS', 'REC Limited', 'EQUITY', 'INR', true),
('PFC.NS', 'Power Finance Corporation Limited', 'EQUITY', 'INR', true),
('NHPC.NS', 'NHPC Limited', 'EQUITY', 'INR', true),
('GAIL.NS', 'GAIL India Limited', 'EQUITY', 'INR', true),
('VEDL.NS', 'Vedanta Limited', 'EQUITY', 'INR', true),
('SAIL.NS', 'Steel Authority of India Limited', 'EQUITY', 'INR', true),
('JINDALSTEL.NS', 'Jindal Steel & Power Limited', 'EQUITY', 'INR', true),
('TATAELXSI.NS', 'Tata Elxsi Limited', 'EQUITY', 'INR', true),
('PERSISTENT.NS', 'Persistent Systems Limited', 'EQUITY', 'INR', true),
('COFORGE.NS', 'Coforge Limited', 'EQUITY', 'INR', true),
('LTIM.NS', 'LTIMindtree Limited', 'EQUITY', 'INR', true),
('MPHASIS.NS', 'Mphasis Limited', 'EQUITY', 'INR', true),
('OFSS.NS', 'Oracle Financial Services Software Limited', 'EQUITY', 'INR', true),
('KPITTECH.NS', 'KPIT Technologies Limited', 'EQUITY', 'INR', true),
('MOTHERSON.NS', 'Samvardhana Motherson International Limited', 'EQUITY', 'INR', true),
('BOSCHLTD.NS', 'Bosch Limited', 'EQUITY', 'INR', true),
('ASHOKLEY.NS', 'Ashok Leyland Limited', 'EQUITY', 'INR', true),
('TVSMOTOR.NS', 'TVS Motor Company Limited', 'EQUITY', 'INR', true),
('CONCOR.NS', 'Container Corporation of India Limited', 'EQUITY', 'INR', true),
('INDIGO.NS', 'InterGlobe Aviation Limited', 'EQUITY', 'INR', true),
('ZYDUSLIFE.NS', 'Zydus Lifesciences Limited', 'EQUITY', 'INR', true),
('LUPIN.NS', 'Lupin Limited', 'EQUITY', 'INR', true),
('AUROPHARMA.NS', 'Aurobindo Pharma Limited', 'EQUITY', 'INR', true),
('TORNTPHARM.NS', 'Torrent Pharmaceuticals Limited', 'EQUITY', 'INR', true),
('ALKEM.NS', 'Alkem Laboratories Limited', 'EQUITY', 'INR', true),
('BIOCON.NS', 'Biocon Limited', 'EQUITY', 'INR', true),
('MAXHEALTH.NS', 'Max Healthcare Institute Limited', 'EQUITY', 'INR', true),
('FORTIS.NS', 'Fortis Healthcare Limited', 'EQUITY', 'INR', true),
('MUTHOOTFIN.NS', 'Muthoot Finance Limited', 'EQUITY', 'INR', true),
('CHOLAFIN.NS', 'Cholamandalam Investment and Finance Company Limited', 'EQUITY', 'INR', true),
('MANAPPURAM.NS', 'Manappuram Finance Limited', 'EQUITY', 'INR', true),
('LICHSGFIN.NS', 'LIC Housing Finance Limited', 'EQUITY', 'INR', true),
('BANKBARODA.NS', 'Bank of Baroda', 'EQUITY', 'INR', true),
('PNB.NS', 'Punjab National Bank', 'EQUITY', 'INR', true),
('CANBK.NS', 'Canara Bank', 'EQUITY', 'INR', true),
('IDFCFIRSTB.NS', 'IDFC First Bank Limited', 'EQUITY', 'INR', true),
('FEDERALBNK.NS', 'The Federal Bank Limited', 'EQUITY', 'INR', true),
('BANDHANBNK.NS', 'Bandhan Bank Limited', 'EQUITY', 'INR', true),
('YESBANK.NS', 'Yes Bank Limited', 'EQUITY', 'INR', true),
('IDBI.NS', 'IDBI Bank Limited', 'EQUITY', 'INR', true),
('INDIANB.NS', 'Indian Bank', 'EQUITY', 'INR', true),
('UNIONBANK.NS', 'Union Bank of India', 'EQUITY', 'INR', true),
('BANKINDIA.NS', 'Bank of India', 'EQUITY', 'INR', true),
('INDUSTOWER.NS', 'Indus Towers Limited', 'EQUITY', 'INR', true),
('IDEA.NS', 'Vodafone Idea Limited', 'EQUITY', 'INR', true),
('ZEEL.NS', 'Zee Entertainment Enterprises Limited', 'EQUITY', 'INR', true),
('PVRINOX.NS', 'PVR INOX Limited', 'EQUITY', 'INR', true),
('ETERNAL.NS', 'Eternal Limited', 'EQUITY', 'INR', true),
('PAYTM.NS', 'One 97 Communications Limited', 'EQUITY', 'INR', true),
('NYKAA.NS', 'FSN E-Commerce Ventures Limited', 'EQUITY', 'INR', true),
('DELHIVERY.NS', 'Delhivery Limited', 'EQUITY', 'INR', true),
('DMART.NS', 'Avenue Supermarts Limited', 'EQUITY', 'INR', true),
('JUBLFOOD.NS', 'Jubilant FoodWorks Limited', 'EQUITY', 'INR', true),
('VBL.NS', 'Varun Beverages Limited', 'EQUITY', 'INR', true),
('MARICO.NS', 'Marico Limited', 'EQUITY', 'INR', true),
('COLPAL.NS', 'Colgate-Palmolive India Limited', 'EQUITY', 'INR', true),
('EMAMILTD.NS', 'Emami Limited', 'EQUITY', 'INR', true),
('GODREJPROP.NS', 'Godrej Properties Limited', 'EQUITY', 'INR', true),
('DLF.NS', 'DLF Limited', 'EQUITY', 'INR', true),
('OBEROIRLTY.NS', 'Oberoi Realty Limited', 'EQUITY', 'INR', true),
('PRESTIGE.NS', 'Prestige Estates Projects Limited', 'EQUITY', 'INR', true),
('LODHA.NS', 'Macrotech Developers Limited', 'EQUITY', 'INR', true),
('SHREECEM.NS', 'Shree Cement Limited', 'EQUITY', 'INR', true),
('AMBUJACEM.NS', 'Ambuja Cements Limited', 'EQUITY', 'INR', true),
('ACC.NS', 'ACC Limited', 'EQUITY', 'INR', true),
('DALBHARAT.NS', 'Dalmia Bharat Limited', 'EQUITY', 'INR', true),
('RAMCOCEM.NS', 'The Ramco Cements Limited', 'EQUITY', 'INR', true),
('INDIACEM.NS', 'The India Cements Limited', 'EQUITY', 'INR', true),
('TATAMTRDVR.NS', 'Tata Motors DVR', 'EQUITY', 'INR', true),
('JSWENERGY.NS', 'JSW Energy Limited', 'EQUITY', 'INR', true),
('TORNTPOWER.NS', 'Torrent Power Limited', 'EQUITY', 'INR', true),
('ADANIGREEN.NS', 'Adani Green Energy Limited', 'EQUITY', 'INR', true),
('ADANIPOWER.NS', 'Adani Power Limited', 'EQUITY', 'INR', true),
('SUZLON.NS', 'Suzlon Energy Limited', 'EQUITY', 'INR', true),
('IREDA.NS', 'Indian Renewable Energy Development Agency Limited', 'EQUITY', 'INR', true),
('INDIANHOTEL.NS', 'The Indian Hotels Company Limited', 'EQUITY', 'INR', true),
('MINDTREE.NS', 'Mindtree Limited', 'EQUITY', 'INR', true),
('GLAND.NS', 'Gland Pharma Limited', 'EQUITY', 'INR', true),
('IPCALAB.NS', 'IPCA Laboratories Limited', 'EQUITY', 'INR', true),
('BALKRISIND.NS', 'Balkrishna Industries Limited', 'EQUITY', 'INR', true),
('APOLLOTYRE.NS', 'Apollo Tyres Limited', 'EQUITY', 'INR', true),
('EXIDEIND.NS', 'Exide Industries Limited', 'EQUITY', 'INR', true),
('ESCORTS.NS', 'Escorts Kubota Limited', 'EQUITY', 'INR', true),
('CUMMINSIND.NS', 'Cummins India Limited', 'EQUITY', 'INR', true),
('POLYCAB.NS', 'Polycab India Limited', 'EQUITY', 'INR', true),
('KEI.NS', 'KEI Industries Limited', 'EQUITY', 'INR', true),
('ASTRAL.NS', 'Astral Limited', 'EQUITY', 'INR', true),
('SUPREMEIND.NS', 'Supreme Industries Limited', 'EQUITY', 'INR', true),
('BATAINDIA.NS', 'Bata India Limited', 'EQUITY', 'INR', true),
('PAGEIND.NS', 'Page Industries Limited', 'EQUITY', 'INR', true),
('MCDOWELL-N.NS', 'United Spirits Limited', 'EQUITY', 'INR', true),
('TATACOMM.NS', 'Tata Communications Limited', 'EQUITY', 'INR', true),
('INDHOTEL.NS', 'Indian Hotels Company Limited', 'EQUITY', 'INR', true),
('IRB.NS', 'IRB Infrastructure Developers Limited', 'EQUITY', 'INR', true),
('DELHIVERY.NS', 'Delhivery Limited', 'EQUITY', 'INR', true),
('JIOFIN.NS', 'Jio Financial Services Limited', 'EQUITY', 'INR', true)

ON CONFLICT (symbol) DO NOTHING;

INSERT INTO instruments (symbol, name, asset_class, currency, tradable)
VALUES
    ('QQQ', 'Invesco QQQ Trust', 'ETF', 'USD', true),
    ('DIA', 'SPDR Dow Jones Industrial Average ETF Trust', 'ETF', 'USD', true),
    ('IWM', 'iShares Russell 2000 ETF', 'ETF', 'USD', true),
    ('VOO', 'Vanguard S&P 500 ETF', 'ETF', 'USD', true),
    ('VTI', 'Vanguard Total Stock Market ETF', 'ETF', 'USD', true),
    ('X:ETH-USD', 'Ethereum against United States Dollar', 'CRYPTO', 'USD', true),
    ('X:SOL-USD', 'Solana against United States Dollar', 'CRYPTO', 'USD', true),
    ('X:XRP-USD', 'XRP against United States Dollar', 'CRYPTO', 'USD', true),
    ('X:DOGE-USD', 'Dogecoin against United States Dollar', 'CRYPTO', 'USD', true),
    ('X:ADA-USD', 'Cardano against United States Dollar', 'CRYPTO', 'USD', true)
ON CONFLICT (symbol) DO NOTHING;


-- ===== from migrations/008_create_admin_user.sql =====
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
    10000.00,
    10000.00,
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


-- ===== from migrations/012_default_account_balance.sql (data part) =====
-- Seeded accounts already exist, so set every current balance to the same starting amount.
UPDATE tradingsystem.accounts
SET cash_balance = 10000.00,
    buying_power = 10000.00,
    last_updated = NOW();

