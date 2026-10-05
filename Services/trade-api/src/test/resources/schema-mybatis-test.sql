-- Test schema for MyBatis mapper integration tests.
-- Uses H2-compatible SQL (no PostgreSQL-specific extensions).
-- Tables are created in the tradingsystem schema to mirror production.
-- H2 requires schema creation before table creation.

CREATE SCHEMA IF NOT EXISTS tradingsystem;

DROP TABLE IF EXISTS tradingsystem.orders;
DROP TABLE IF EXISTS tradingsystem.positions;
DROP TABLE IF EXISTS tradingsystem.users;
DROP TABLE IF EXISTS tradingsystem.instruments;
DROP TABLE IF EXISTS tradingsystem.accounts;

CREATE TABLE tradingsystem.accounts (
    id BIGINT PRIMARY KEY,
    account_reference VARCHAR(64) NOT NULL UNIQUE,
    cash_balance NUMERIC(18,2) NOT NULL,
    buying_power NUMERIC(18,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tradingsystem.instruments (
    symbol VARCHAR(20) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    asset_class VARCHAR(20) NOT NULL,
    currency CHAR(3) NOT NULL,
    tradable BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE tradingsystem.positions (
    account_id BIGINT NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    qty INT NOT NULL,
    avg_cost NUMERIC(18,2) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id, symbol),
    CONSTRAINT fk_positions_account FOREIGN KEY (account_id) REFERENCES tradingsystem.accounts (id),
    CONSTRAINT fk_positions_instrument FOREIGN KEY (symbol) REFERENCES tradingsystem.instruments (symbol)
);

CREATE TABLE tradingsystem.orders (
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
    CONSTRAINT fk_orders_account FOREIGN KEY (account_id) REFERENCES tradingsystem.accounts (id),
    CONSTRAINT fk_orders_instrument FOREIGN KEY (symbol) REFERENCES tradingsystem.instruments (symbol)
);

CREATE TABLE tradingsystem.users (
    id UUID PRIMARY KEY,
    account_id BIGINT NOT NULL UNIQUE,
    username VARCHAR(64) NOT NULL UNIQUE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    email VARCHAR(255),
    phone_no VARCHAR(30),
    roles VARCHAR ARRAY NOT NULL,
    created_on TIMESTAMP NOT NULL,
    CONSTRAINT fk_users_account
        FOREIGN KEY (account_id)
        REFERENCES tradingsystem.accounts (id)
);

-- Synonyms in PUBLIC schema so unqualified queries (e.g. FROM accounts) resolve seamlessly in H2
CREATE SYNONYM IF NOT EXISTS accounts FOR tradingsystem.accounts;
CREATE SYNONYM IF NOT EXISTS instruments FOR tradingsystem.instruments;
CREATE SYNONYM IF NOT EXISTS positions FOR tradingsystem.positions;
CREATE SYNONYM IF NOT EXISTS orders FOR tradingsystem.orders;
CREATE SYNONYM IF NOT EXISTS users FOR tradingsystem.users;
