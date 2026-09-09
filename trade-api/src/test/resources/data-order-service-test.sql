INSERT INTO accounts (id, account_reference, cash_balance, buying_power, status, version, last_updated)
VALUES (1001, 'ACC-1001', 100000.00, 100000.00, 'ACTIVE', 1, TIMESTAMP '2026-09-01 10:00:00');

INSERT INTO instruments (symbol, name, asset_class, currency, tradable)
VALUES ('AAPL', 'Apple Inc', 'EQUITY', 'USD', TRUE);

INSERT INTO positions (account_id, symbol, qty, avg_cost)
VALUES (1001, 'AAPL', 15, 171.50);

INSERT INTO orders (id, account_id, symbol, side, order_type, qty, price, status, idempotency_key, created_on)
VALUES
    ('11111111-1111-1111-1111-111111111111', 1001, 'AAPL', 'BUY', 'LIMIT', 1, 100.00, 'NEW', 'idem-existing-1', TIMESTAMP '2026-09-01 10:05:00'),
    ('22222222-2222-2222-2222-222222222222', 1001, 'AAPL', 'BUY', 'LIMIT', 2, 200.00, 'FILLED', 'idem-filled-1', TIMESTAMP '2026-09-01 10:10:00');

