INSERT INTO accounts (id, account_reference, cash_balance, buying_power, status, version, last_updated)
VALUES (1001, 'ACC-1001', 100000.00, 100000.00, 'ACTIVE', 1, TIMESTAMP '2026-09-01 10:00:00');

INSERT INTO instruments (symbol, name, asset_class, currency, tradable)
VALUES ('AAPL', 'Apple Inc', 'EQUITY', 'USD', TRUE);

INSERT INTO positions (account_id, symbol, qty, avg_cost)
VALUES (1001, 'AAPL', 15, 171.50);

