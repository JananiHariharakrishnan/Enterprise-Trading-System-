BEGIN;

-- Every new account starts with 10,000 cash and buying power
-- when an insert does not supply those columns.

ALTER TABLE tradingsystem.accounts
    ALTER COLUMN cash_balance SET DEFAULT 10000.00,
    ALTER COLUMN buying_power SET DEFAULT 10000.00;

-- Seeded accounts already exist, so set every current balance to the same starting amount.
UPDATE tradingsystem.accounts
SET cash_balance = 10000.00,
    buying_power = 10000.00,
    last_updated = NOW();

COMMIT;
