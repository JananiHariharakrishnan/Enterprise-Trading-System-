INSERT INTO tradingsystem.accounts (
    id, account_reference, cash_balance, buying_power,
    status, version, last_updated
)
VALUES
    (1001, 'ACC-1001', 100000.00, 100000.00, 'ACTIVE', 1, TIMESTAMP '2026-09-01 10:00:00'),
    (2002, 'ACC-2002', 50000.00, 50000.00, 'ACTIVE', 1, TIMESTAMP '2026-09-01 11:00:00');

INSERT INTO tradingsystem.instruments (symbol, name, asset_class, currency, tradable)
VALUES
    ('AAPL', 'Apple Inc', 'EQUITY', 'USD', TRUE),
    ('MSFT', 'Microsoft Corp', 'EQUITY', 'USD', TRUE),
    ('SPY', 'SPDR S&P 500 ETF Trust', 'ETF', 'USD', TRUE);

INSERT INTO tradingsystem.watchlists (id, account_id, name, created_on, updated_on)
VALUES
    (10, 1001, 'Tech', TIMESTAMP '2026-09-10 09:00:00', TIMESTAMP '2026-09-10 09:00:00'),
    (20, 2002, 'Favorites', TIMESTAMP '2026-09-10 09:05:00', TIMESTAMP '2026-09-10 09:05:00');

INSERT INTO tradingsystem.watchlist_entries (watchlist_id, symbol, added_on)
VALUES
    (10, 'AAPL', TIMESTAMP '2026-09-10 09:10:00'),
    (20, 'SPY', TIMESTAMP '2026-09-10 09:15:00');

INSERT INTO tradingsystem.price_alerts (
    id, account_id, watchlist_id, symbol, direction, threshold,
    status, last_condition_met, notification_status, notification_attempts,
    created_on, updated_on
)
VALUES (
    501, 1001, 10, 'AAPL', 'ABOVE_OR_EQUAL', 240.00,
    'ACTIVE', FALSE, 'PENDING', 0,
    TIMESTAMP '2026-09-11 08:00:00', TIMESTAMP '2026-09-11 08:00:00'
);

