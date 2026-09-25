-- =============================================================================
-- Enterprise Trading Platform
-- Migration 001: Initial trading schema
-- PostgreSQL
--
-- Covers:
--   instruments, accounts, users, orders, executions,
--   positions, balance_history, watchlist
--
-- Money is stored as NUMERIC(18,2).
-- Status/type values are VARCHAR + CHECK constraints rather than PostgreSQL ENUMs.
-- =============================================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- -----------------------------------------------------------------------------
-- INSTRUMENTS
-- A non-tradable instrument remains in the database so historical orders,
-- executions, positions and watchlists can still resolve the symbol.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS instruments(
    symbol          VARCHAR(20) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    asset_class     VARCHAR(20) NOT NULL,
    currency        CHAR(3) NOT NULL,
    tradable        BOOLEAN NOT NULL DEFAULT TRUE,

    CONSTRAINT pk_instruments PRIMARY KEY (symbol),

    CONSTRAINT ck_instruments_asset_class
        CHECK (asset_class IN ('EQUITY', 'ETF', 'FX', 'CRYPTO', 'BOND')),

    CONSTRAINT ck_instruments_currency
        CHECK (currency ~ '^[A-Z]{3}$')
);

-- -----------------------------------------------------------------------------
-- ACCOUNTS
--
-- id                = internal surrogate PK used by foreign keys.
-- account_reference = stable business/customer account reference (e.g. ETP-2201).
-- cash_balance      = current available cash.
-- buying_power      = amount currently available for new orders.
-- version           = optimistic locking counter.
--
-- CLOSED accounts are retained and must never be deleted. A trigger below
-- prevents CLOSED -> ACTIVE/SUSPENDED transitions.
-- SUSPENDED accounts may be returned to ACTIVE.
-- -----------------------------------------------------------------------------

CREATE TABLE  IF NOT EXISTS accounts(
    id                BIGINT GENERATED ALWAYS AS IDENTITY,
    account_reference VARCHAR(64)  NOT NULL,
    cash_balance      NUMERIC(18,2) NOT NULL,
    buying_power      NUMERIC(18,2) NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    version           INT          NOT NULL DEFAULT 0,
    last_updated      TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_accounts PRIMARY KEY (id),

    CONSTRAINT uq_accounts_reference
        UNIQUE (account_reference),

    CONSTRAINT ck_accounts_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),

    CONSTRAINT ck_accounts_cash_balance_non_negative
        CHECK (cash_balance >= 0),

    CONSTRAINT ck_accounts_buying_power_non_negative
        CHECK (buying_power >= 0),

    CONSTRAINT ck_accounts_version_non_negative
        CHECK (version >= 0)
);

-- -----------------------------------------------------------------------------
-- USERS
-- One user/holder record per trading account.
-- `id` is the stable auth identity used as JWT `sub` and by refresh tokens.
-- `account_id` remains unique, preserving the 1:1 relationship with accounts.
-- -----------------------------------------------------------------------------

CREATE TABLE  IF NOT EXISTS users(
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    account_id       BIGINT       NOT NULL,
    username         VARCHAR(64)  NOT NULL,
    first_name       VARCHAR(100),
    last_name        VARCHAR(100),
    email            VARCHAR(255),
    phone_no         VARCHAR(30),
    roles            TEXT[]       NOT NULL DEFAULT ARRAY['CUSTOMER']::TEXT[],
    created_on       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_users PRIMARY KEY (id),

    CONSTRAINT uq_users_account_id UNIQUE (account_id),

    CONSTRAINT uq_users_username UNIQUE (username),

    CONSTRAINT fk_users_account
        FOREIGN KEY (account_id)
        REFERENCES accounts (id),

    CONSTRAINT uq_users_email UNIQUE (email),

    CONSTRAINT chk_users_username_format
        CHECK (username ~ '^[a-zA-Z0-9._-]+$'),

    CONSTRAINT chk_users_roles_non_empty
        CHECK (array_length(roles, 1) >= 1),

    CONSTRAINT chk_users_roles_valid
        CHECK (roles <@ ARRAY['CUSTOMER', 'ADMIN']::TEXT[])
);

-- -----------------------------------------------------------------------------
-- CREDENTIALS
-- Passwords are stored separately from user profile fields.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS credentials(
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    account_id    BIGINT       NOT NULL,
    username      VARCHAR(64)  NOT NULL,
    password_hash TEXT         NOT NULL,
    params        JSONB        NOT NULL DEFAULT '{}'::JSONB,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_credentials PRIMARY KEY (id),

    CONSTRAINT uq_credentials_account_id UNIQUE (account_id),

    CONSTRAINT uq_credentials_username UNIQUE (username),

    CONSTRAINT fk_credentials_account
        FOREIGN KEY (account_id)
        REFERENCES accounts (id)
        ON DELETE CASCADE
);

-- -----------------------------------------------------------------------------
-- REFRESH TOKENS
-- Stores only a hash of the opaque refresh-token secret.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS refresh_tokens(
    id            UUID         NOT NULL,
    session_id    UUID         NOT NULL,
    user_id       UUID         NOT NULL,
    token_hash    TEXT         NOT NULL,
    algorithm     VARCHAR(32)  NOT NULL DEFAULT 'bcrypt',
    expires_at    TIMESTAMPTZ  NOT NULL,
    consumed_at   TIMESTAMPTZ,
    revoked_at    TIMESTAMPTZ,
    replaced_by   UUID,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES users (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_refresh_tokens_replaced_by
        FOREIGN KEY (replaced_by)
        REFERENCES refresh_tokens (id)
        ON DELETE SET NULL
);

-- -----------------------------------------------------------------------------
-- ORDERS
--
-- Every received order is recorded, including rejected/cancelled orders.
-- There is deliberately no PARTIALLY_FILLED state.
-- NEW is the only working state; FILLED, REJECTED and CANCELLED are terminal.
--
-- idempotency_key is UNIQUE at the database level. This is the concurrency-safe
-- duplicate-order guarantee: do not replace it with SELECT-then-INSERT logic.
-- -----------------------------------------------------------------------------

CREATE TABLE  IF NOT EXISTS orders(
    id               UUID          NOT NULL,
    account_id       BIGINT        NOT NULL,
    symbol           VARCHAR(20)   NOT NULL,
    side             VARCHAR(4)    NOT NULL,
    order_type       VARCHAR(10)   NOT NULL,
    qty              INT           NOT NULL,
    price            NUMERIC(18,2) NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    idempotency_key  VARCHAR(100)  NOT NULL,
    created_on       TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_orders PRIMARY KEY (id),

    CONSTRAINT fk_orders_account
        FOREIGN KEY (account_id)
        REFERENCES accounts (id),

    CONSTRAINT fk_orders_instrument
        FOREIGN KEY (symbol)
        REFERENCES instruments (symbol),

    CONSTRAINT uq_orders_idempotency_key
        UNIQUE (idempotency_key),

    CONSTRAINT ck_orders_side
        CHECK (side IN ('BUY', 'SELL')),

    CONSTRAINT ck_orders_order_type
        CHECK (order_type IN ('MARKET', 'LIMIT')),

    CONSTRAINT ck_orders_status
        CHECK (status IN ('NEW', 'FILLED', 'REJECTED', 'CANCELLED')),

    CONSTRAINT ck_orders_qty_positive
        CHECK (qty > 0),

    CONSTRAINT ck_orders_price_positive
        CHECK (price > 0)
);

-- -----------------------------------------------------------------------------
-- EXECUTIONS
-- One order may have multiple executions/fills.
-- Each execution records the actual quantity, price and time.
-- -----------------------------------------------------------------------------

CREATE TABLE  IF NOT EXISTS executions(
    id            BIGINT GENERATED ALWAYS AS IDENTITY,
    order_id      UUID          NOT NULL,
    quantity      INT           NOT NULL,
    price         NUMERIC(18,2) NOT NULL,
    executed_at   TIMESTAMP     NOT NULL DEFAULT NOW(),
    created_at    TIMESTAMP     NOT NULL DEFAULT NOW(),
    event_id      UUID,

    CONSTRAINT pk_executions PRIMARY KEY (id),

    CONSTRAINT fk_executions_order
        FOREIGN KEY (order_id)
        REFERENCES orders (id),

    CONSTRAINT uq_executions_event_id
        UNIQUE (event_id),

    CONSTRAINT ck_executions_quantity_positive
        CHECK (quantity > 0),

    CONSTRAINT ck_executions_price_positive
        CHECK (price > 0)
);

-- -----------------------------------------------------------------------------
-- POSITIONS
-- One net position per account + instrument.
-- Short selling is out of scope, so quantity cannot be negative.
-- -----------------------------------------------------------------------------

CREATE TABLE  IF NOT EXISTS positions(
    account_id   BIGINT        NOT NULL,
    symbol       VARCHAR(20)   NOT NULL,
    qty          INT           NOT NULL,
    avg_cost     NUMERIC(18,2) NOT NULL,
    version      INT           NOT NULL DEFAULT 0,

    CONSTRAINT pk_positions PRIMARY KEY (account_id, symbol),

    CONSTRAINT fk_positions_account
        FOREIGN KEY (account_id)
        REFERENCES accounts (id),

    CONSTRAINT fk_positions_instrument
        FOREIGN KEY (symbol)
        REFERENCES instruments (symbol),

    CONSTRAINT ck_positions_qty_non_negative
        CHECK (qty >= 0),

    CONSTRAINT ck_positions_avg_cost_non_negative
        CHECK (avg_cost >= 0),

    CONSTRAINT ck_positions_version_non_negative
        CHECK (version >= 0)
);

-- -----------------------------------------------------------------------------
-- BALANCE HISTORY
--
-- amount is a signed cash movement:
--   DEPOSIT    -> positive
--   WITHDRAWAL -> negative
--   TRADE      -> positive or negative depending on the trade
--   FEE        -> negative
--
-- related_order_id is nullable because deposits/withdrawals do not require
-- an order.
-- -----------------------------------------------------------------------------

CREATE TABLE  IF NOT EXISTS balance_history(
    id                BIGINT GENERATED ALWAYS AS IDENTITY,
    account_id        BIGINT        NOT NULL,
    type              VARCHAR(20)   NOT NULL,
    amount            NUMERIC(18,2) NOT NULL,
    related_order_id  UUID,

    CONSTRAINT pk_balance_history PRIMARY KEY (id),

    CONSTRAINT fk_balance_history_account
        FOREIGN KEY (account_id)
        REFERENCES accounts (id),

    CONSTRAINT fk_balance_history_order
        FOREIGN KEY (related_order_id)
        REFERENCES orders (id),

    CONSTRAINT ck_balance_history_type
        CHECK (type IN ('TRADE', 'DEPOSIT', 'WITHDRAWAL', 'FEE')),

    CONSTRAINT ck_balance_history_amount_non_zero
        CHECK (amount <> 0)
);

-- -----------------------------------------------------------------------------
-- WATCHLIST
-- Many-to-many relationship between accounts and instruments.
-- Composite PK prevents the same account from adding the same symbol twice.
-- -----------------------------------------------------------------------------

CREATE TABLE  IF NOT EXISTS watchlist(
    account_id   BIGINT       NOT NULL,
    symbol       VARCHAR(20)  NOT NULL,
    added_on     TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_watchlist PRIMARY KEY (account_id, symbol),

    CONSTRAINT fk_watchlist_account
        FOREIGN KEY (account_id)
        REFERENCES accounts (id),

    CONSTRAINT fk_watchlist_instrument
        FOREIGN KEY (symbol)
        REFERENCES instruments (symbol)
);

-- -----------------------------------------------------------------------------
-- ACCOUNT STATUS TRANSITION RULE
--
-- ACTIVE <-> SUSPENDED is allowed.
-- ACTIVE -> CLOSED is allowed.
-- SUSPENDED -> CLOSED is allowed.
-- CLOSED -> anything is forbidden.
--
-- This keeps closed accounts in the database permanently while still allowing
-- reversible suspension.
-- -----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION prevent_closed_account_reactivation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.status = 'CLOSED' AND NEW.status <> 'CLOSED' THEN
        RAISE EXCEPTION 'Closed account % cannot be reactivated', OLD.id;
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_accounts_prevent_closed_reactivation
BEFORE UPDATE OF status ON accounts
FOR EACH ROW
EXECUTE FUNCTION prevent_closed_account_reactivation();

-- -----------------------------------------------------------------------------
-- INDEXES
-- Foreign-key indexes are added for common account/instrument/order lookups.
-- -----------------------------------------------------------------------------

CREATE INDEX ix_orders_account_created
    ON orders (account_id, created_on DESC);

CREATE INDEX ix_orders_status_new
    ON orders (status)
    WHERE status = 'NEW';

CREATE INDEX ix_orders_symbol_created
    ON orders (symbol, created_on DESC);

CREATE INDEX ix_executions_order
    ON executions (order_id);

CREATE INDEX ix_executions_event_id
    ON executions (event_id);

CREATE INDEX ix_positions_symbol
    ON positions (symbol);

CREATE INDEX ix_positions_account_symbol_version
    ON positions (account_id, symbol, version);

CREATE INDEX ix_balance_history_account
    ON balance_history (account_id);

CREATE INDEX ix_balance_history_order
    ON balance_history (related_order_id);

CREATE INDEX ix_watchlist_symbol
    ON watchlist (symbol);

CREATE INDEX ix_refresh_tokens_user_id
    ON refresh_tokens (user_id);

CREATE INDEX ix_refresh_tokens_session_id
    ON refresh_tokens (session_id);

CREATE INDEX ix_refresh_tokens_expires_at
    ON refresh_tokens (expires_at);

COMMENT ON COLUMN executions.event_id IS
    'Kafka event UUID for idempotency: if same event_id exists, execution already applied';

COMMENT ON COLUMN executions.created_at IS
    'Timestamp when execution was recorded, separate from executed_at (market time)';

COMMENT ON COLUMN positions.version IS
    'Optimistic lock version: incremented on each successful update. Used to detect concurrent modifications.';

COMMIT;