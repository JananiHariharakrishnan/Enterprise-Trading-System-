INSERT INTO accounts (
    id, account_reference, cash_balance, buying_power,
    status, version, last_updated
)
VALUES (
    1001, 'ACC-1001', 100000.00, 100000.00,
    'ACTIVE', 1, TIMESTAMP '2026-09-01 10:00:00'
);

INSERT INTO accounts (
    id, account_reference, cash_balance, buying_power,
    status, version, last_updated
)
VALUES (
    2002, 'ACC-2002', 50000.00, 50000.00,
    'ACTIVE', 1, TIMESTAMP '2026-09-01 11:00:00'
);

INSERT INTO instruments (symbol, name, asset_class, currency, tradable)
VALUES ('AAPL', 'Apple Inc', 'EQUITY', 'USD', TRUE);

INSERT INTO instruments (symbol, name, asset_class, currency, tradable)
VALUES ('SPY', 'SPDR S&P 500 ETF Trust', 'ETF', 'USD', TRUE);

INSERT INTO positions (account_id, symbol, qty, avg_cost)
VALUES (1001, 'AAPL', 15, 171.50);

INSERT INTO positions (account_id, symbol, qty, avg_cost)
VALUES (2002, 'AAPL', 20, 170.00);

INSERT INTO positions (account_id, symbol, qty, avg_cost)
VALUES (1001, 'SPY', 0, 500.00);

INSERT INTO users (
    account_id, first_name, last_name, email, phone_no, password_hash
)
VALUES (
    1001, 'Ananya', 'Iyer',
    'ananya.iyer@example.com',
    '+91-9000000001',
    'test-password'
);

INSERT INTO users (
    account_id, first_name, last_name, email, phone_no, password_hash
)
VALUES (
    2002, 'Rohit', 'Deshpande',
    'rohit@example.com',
    '+91-9000000002',
    'test-password'
);