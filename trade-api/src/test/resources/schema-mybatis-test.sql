DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS positions;
DROP TABLE IF EXISTS instruments;
DROP TABLE IF EXISTS accounts;

CREATE TABLE accounts (
    id BIGINT PRIMARY KEY,
    account_reference VARCHAR(64) NOT NULL UNIQUE,
    cash_balance NUMERIC(18,2) NOT NULL,
    buying_power NUMERIC(18,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE instruments (
    symbol VARCHAR(20) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    asset_class VARCHAR(20) NOT NULL,
    currency CHAR(3) NOT NULL,
    tradable BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE positions (
    account_id BIGINT NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    qty INT NOT NULL,
    avg_cost NUMERIC(18,2) NOT NULL,
    PRIMARY KEY (account_id, symbol),
    CONSTRAINT fk_positions_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_positions_instrument FOREIGN KEY (symbol) REFERENCES instruments (symbol)
);

CREATE TABLE orders (
    id UUID PRIMARY KEY,
    account_id BIGINT NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    side VARCHAR(4) NOT NULL,
    order_type VARCHAR(10) NOT NULL,
    qty INT NOT NULL,
    price NUMERIC(18,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    created_on TIMESTAMP NOT NULL,
    CONSTRAINT fk_orders_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT fk_orders_instrument FOREIGN KEY (symbol) REFERENCES instruments (symbol)
);

