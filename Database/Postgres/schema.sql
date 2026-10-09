-- =============================================================================
-- Enterprise Trading Platform - schema
-- Built from migrations/ 001, 003, 004, 006, 007, 009, 010, 011 and the
-- schema part of 012, in filename order. Seed data is in seed_data.sql.
-- Run against an empty database inside one transaction:
--   psql -v ON_ERROR_STOP=1 --single-transaction -f schema.sql
-- =============================================================================

-- ===== from migrations/001_create_trading_schema.sql =====
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


CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- -----------------------------------------------------------------------------
-- SCHEMAS
-- -----------------------------------------------------------------------------

CREATE SCHEMA IF NOT EXISTS tradingsystem;
CREATE SCHEMA IF NOT EXISTS authentication;

-- Set search_path for the duration of this session so FK resolution works
-- without fully-qualified names inside this script.
SET search_path TO tradingsystem, authentication, public;

-- -----------------------------------------------------------------------------
-- INSTRUMENTS
-- A non-tradable instrument remains in the database so historical orders,
-- executions, positions and watchlists can still resolve the symbol.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS tradingsystem.instruments(
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

CREATE TABLE IF NOT EXISTS tradingsystem.accounts(
    id                BIGINT GENERATED ALWAYS AS IDENTITY,
    account_reference VARCHAR(64)  NOT NULL,
    cash_balance      NUMERIC(18,2) NOT NULL DEFAULT 10000.00,
    buying_power      NUMERIC(18,2) NOT NULL DEFAULT 10000.00,
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

CREATE TABLE IF NOT EXISTS tradingsystem.users(
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
        REFERENCES tradingsystem.accounts (id),

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
-- Lives in the authentication schema so that access can be restricted
-- independently from the trading data.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS authentication.credentials(
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
        REFERENCES tradingsystem.accounts (id)
        ON DELETE CASCADE
);

-- -----------------------------------------------------------------------------
-- REFRESH TOKENS
-- Stores only a hash of the opaque refresh-token secret.
-- Lives in the authentication schema alongside credentials.
-- user_id is a cross-schema FK into tradingsystem.users.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS authentication.refresh_tokens(
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
        REFERENCES tradingsystem.users (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_refresh_tokens_replaced_by
        FOREIGN KEY (replaced_by)
        REFERENCES authentication.refresh_tokens (id)
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

CREATE TABLE IF NOT EXISTS tradingsystem.orders(
    id               UUID          NOT NULL,
    account_id       BIGINT        NOT NULL,
    symbol           VARCHAR(20)   NOT NULL,
    side             VARCHAR(4)    NOT NULL,
    order_type       VARCHAR(10)   NOT NULL,
    qty              INT           NOT NULL,
    price            NUMERIC(18,2),
    status           VARCHAR(20)   NOT NULL,
    idempotency_key  VARCHAR(100)  NOT NULL,
    created_on       TIMESTAMP     NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_orders PRIMARY KEY (id),

    CONSTRAINT fk_orders_account
        FOREIGN KEY (account_id)
        REFERENCES tradingsystem.accounts (id),

    CONSTRAINT fk_orders_instrument
        FOREIGN KEY (symbol)
        REFERENCES tradingsystem.instruments (symbol),

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
        CHECK (price IS NULL OR price > 0)
);

-- -----------------------------------------------------------------------------
-- EXECUTIONS
-- One order may have multiple executions/fills.
-- Each execution records the actual quantity, price and time.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS tradingsystem.executions(
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
        REFERENCES tradingsystem.orders (id),

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

CREATE TABLE IF NOT EXISTS tradingsystem.positions(
    account_id   BIGINT        NOT NULL,
    symbol       VARCHAR(20)   NOT NULL,
    qty          INT           NOT NULL,
    avg_cost     NUMERIC(18,2) NOT NULL,
    version      INT           NOT NULL DEFAULT 0,

    CONSTRAINT pk_positions PRIMARY KEY (account_id, symbol),

    CONSTRAINT fk_positions_account
        FOREIGN KEY (account_id)
        REFERENCES tradingsystem.accounts (id),

    CONSTRAINT fk_positions_instrument
        FOREIGN KEY (symbol)
        REFERENCES tradingsystem.instruments (symbol),

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

CREATE TABLE IF NOT EXISTS tradingsystem.balance_history(
    id                BIGINT GENERATED ALWAYS AS IDENTITY,
    account_id        BIGINT        NOT NULL,
    type              VARCHAR(20)   NOT NULL,
    amount            NUMERIC(18,2) NOT NULL,
    related_order_id  UUID,

    CONSTRAINT pk_balance_history PRIMARY KEY (id),

    CONSTRAINT fk_balance_history_account
        FOREIGN KEY (account_id)
        REFERENCES tradingsystem.accounts (id),

    CONSTRAINT fk_balance_history_order
        FOREIGN KEY (related_order_id)
        REFERENCES tradingsystem.orders (id),

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

CREATE TABLE IF NOT EXISTS tradingsystem.watchlist(
    account_id   BIGINT       NOT NULL,
    symbol       VARCHAR(20)  NOT NULL,
    added_on     TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_watchlist PRIMARY KEY (account_id, symbol),

    CONSTRAINT fk_watchlist_account
        FOREIGN KEY (account_id)
        REFERENCES tradingsystem.accounts (id),

    CONSTRAINT fk_watchlist_instrument
        FOREIGN KEY (symbol)
        REFERENCES tradingsystem.instruments (symbol)
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

CREATE OR REPLACE FUNCTION tradingsystem.prevent_closed_account_reactivation()
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
BEFORE UPDATE OF status ON tradingsystem.accounts
FOR EACH ROW
EXECUTE FUNCTION tradingsystem.prevent_closed_account_reactivation();

-- -----------------------------------------------------------------------------
-- INDEXES
-- Foreign-key indexes are added for common account/instrument/order lookups.
-- -----------------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS ix_orders_account_created
    ON tradingsystem.orders (account_id, created_on DESC);

CREATE INDEX IF NOT EXISTS ix_orders_status_new
    ON tradingsystem.orders (status)
    WHERE status = 'NEW';

CREATE INDEX IF NOT EXISTS ix_orders_symbol_created
    ON tradingsystem.orders (symbol, created_on DESC);

CREATE INDEX IF NOT EXISTS ix_executions_order
    ON tradingsystem.executions (order_id);

CREATE INDEX IF NOT EXISTS ix_executions_event_id
    ON tradingsystem.executions (event_id);

CREATE INDEX IF NOT EXISTS ix_positions_symbol
    ON tradingsystem.positions (symbol);

CREATE INDEX IF NOT EXISTS ix_positions_account_symbol_version
    ON tradingsystem.positions (account_id, symbol, version);

CREATE INDEX IF NOT EXISTS ix_balance_history_account
    ON tradingsystem.balance_history (account_id);

CREATE INDEX IF NOT EXISTS ix_balance_history_order
    ON tradingsystem.balance_history (related_order_id);

CREATE INDEX IF NOT EXISTS ix_watchlist_symbol
    ON tradingsystem.watchlist (symbol);

CREATE INDEX IF NOT EXISTS ix_refresh_tokens_user_id
    ON authentication.refresh_tokens (user_id);

CREATE INDEX IF NOT EXISTS ix_refresh_tokens_session_id
    ON authentication.refresh_tokens (session_id);

CREATE INDEX IF NOT EXISTS ix_refresh_tokens_expires_at
    ON authentication.refresh_tokens (expires_at);

COMMENT ON COLUMN tradingsystem.executions.event_id IS
    'Kafka event UUID for idempotency: if same event_id exists, execution already applied';

COMMENT ON COLUMN tradingsystem.executions.created_at IS
    'Timestamp when execution was recorded, separate from executed_at (market time)';

COMMENT ON COLUMN tradingsystem.positions.version IS
    'Optimistic lock version: incremented on each successful update. Used to detect concurrent modifications.';

-- Set the database-level search_path so all connections default to the right schemas
ALTER DATABASE trading SET search_path TO tradingsystem, authentication, public;


-- ===== from migrations/003_split_schemas.sql =====
-- =============================================================================
-- Enterprise Trading Platform
-- Migration 003: Split public schema into authentication + tradingsystem
-- PostgreSQL
--
-- Purpose:
--   Move all tables from the default public schema into two dedicated schemas:
--     - tradingsystem : instruments, accounts, users, orders, executions,
--                       positions, balance_history, watchlist
--     - authentication: credentials, refresh_tokens
--
-- Strategy:
--   1. Create the two new schemas.
--   2. Drop cross-schema FK before moving tables (refresh_tokens.user_id → users).
--   3. Move tables with ALTER TABLE … SET SCHEMA.
--   4. Re-create the cross-schema FK with the fully-qualified reference.
--   5. Move indexes (they follow the table automatically in PostgreSQL).
--   6. Move the trigger function and trigger.
--   7. Update search_path so bare-name queries still work during transition.
--
-- Safe to run on a live database. Each ALTER TABLE SET SCHEMA acquires an
-- ACCESS EXCLUSIVE lock for the duration of the rename only. The lock is
-- released before the next statement.
--
-- Idempotent: IF NOT EXISTS guards on schema creation; the SET SCHEMA
-- operations are skipped by the surrounding DO block if the table is already
-- in the target schema.
-- =============================================================================


-- -----------------------------------------------------------------------------
-- STEP 1: Create schemas
-- -----------------------------------------------------------------------------

CREATE SCHEMA IF NOT EXISTS tradingsystem;
CREATE SCHEMA IF NOT EXISTS authentication;

-- -----------------------------------------------------------------------------
-- STEP 2: Drop the cross-schema FK that would block moving users before
--         refresh_tokens (or vice-versa).
--         We re-create it in step 6 after both tables are in their schemas.
-- -----------------------------------------------------------------------------

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_refresh_tokens_user'
          AND conrelid = to_regclass('public.refresh_tokens')
    ) THEN
        ALTER TABLE public.refresh_tokens
            DROP CONSTRAINT fk_refresh_tokens_user;
    END IF;
END;
$$;

-- Also drop the self-referential FK on refresh_tokens so the table can move
-- cleanly; it will be re-created after the move.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_refresh_tokens_replaced_by'
          AND conrelid = to_regclass('public.refresh_tokens')
    ) THEN
        ALTER TABLE public.refresh_tokens
            DROP CONSTRAINT fk_refresh_tokens_replaced_by;
    END IF;
END;
$$;

-- -----------------------------------------------------------------------------
-- STEP 3: Move tradingsystem tables from public → tradingsystem
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    tbl TEXT;
    tables TEXT[] := ARRAY[
        'instruments',
        'accounts',
        'users',
        'orders',
        'executions',
        'positions',
        'balance_history',
        'watchlist'
    ];
BEGIN
    FOREACH tbl IN ARRAY tables
    LOOP
        IF EXISTS (
            SELECT 1 FROM pg_tables
            WHERE schemaname = 'public' AND tablename = tbl
        ) THEN
            EXECUTE format('ALTER TABLE public.%I SET SCHEMA tradingsystem', tbl);
            RAISE NOTICE 'Moved public.% → tradingsystem.%', tbl, tbl;
        ELSE
            RAISE NOTICE 'Table % not found in public schema (already moved?)', tbl;
        END IF;
    END LOOP;
END;
$$;

-- -----------------------------------------------------------------------------
-- STEP 4: Move authentication tables from public → authentication
-- -----------------------------------------------------------------------------

DO $$
DECLARE
    tbl TEXT;
    tables TEXT[] := ARRAY['credentials', 'refresh_tokens'];
BEGIN
    FOREACH tbl IN ARRAY tables
    LOOP
        IF EXISTS (
            SELECT 1 FROM pg_tables
            WHERE schemaname = 'public' AND tablename = tbl
        ) THEN
            EXECUTE format('ALTER TABLE public.%I SET SCHEMA authentication', tbl);
            RAISE NOTICE 'Moved public.% → authentication.%', tbl, tbl;
        ELSE
            RAISE NOTICE 'Table % not found in public schema (already moved?)', tbl;
        END IF;
    END LOOP;
END;
$$;

-- -----------------------------------------------------------------------------
-- STEP 5: Re-create the cross-schema FKs on refresh_tokens
-- -----------------------------------------------------------------------------

-- refresh_tokens.user_id → tradingsystem.users.id  (cross-schema)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_refresh_tokens_user'
          AND conrelid = to_regclass('authentication.refresh_tokens')
    ) THEN
        ALTER TABLE authentication.refresh_tokens
            ADD CONSTRAINT fk_refresh_tokens_user
                FOREIGN KEY (user_id)
                REFERENCES tradingsystem.users (id)
                ON DELETE CASCADE;
    END IF;
END;
$$;

-- refresh_tokens.replaced_by → authentication.refresh_tokens.id  (self-ref)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_refresh_tokens_replaced_by'
          AND conrelid = to_regclass('authentication.refresh_tokens')
    ) THEN
        ALTER TABLE authentication.refresh_tokens
            ADD CONSTRAINT fk_refresh_tokens_replaced_by
                FOREIGN KEY (replaced_by)
                REFERENCES authentication.refresh_tokens (id)
                ON DELETE SET NULL;
    END IF;
END;
$$;

-- -----------------------------------------------------------------------------
-- STEP 6: Move the trigger function into tradingsystem schema
-- Note: PostgreSQL functions are schema-scoped; recreate in tradingsystem.
-- -----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION tradingsystem.prevent_closed_account_reactivation()
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

-- Drop old trigger (references old public function) and recreate on moved table
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname = 'trg_accounts_prevent_closed_reactivation'
          AND tgrelid = to_regclass('tradingsystem.accounts')
    ) THEN
        DROP TRIGGER trg_accounts_prevent_closed_reactivation
            ON tradingsystem.accounts;
    END IF;
END;
$$;

CREATE TRIGGER trg_accounts_prevent_closed_reactivation
BEFORE UPDATE OF status ON tradingsystem.accounts
FOR EACH ROW
EXECUTE FUNCTION tradingsystem.prevent_closed_account_reactivation();

-- Drop the old public function now that it has been superseded
DROP FUNCTION IF EXISTS public.prevent_closed_account_reactivation();

-- -----------------------------------------------------------------------------
-- STEP 7: Update database-level search_path so bare table names in existing
--         queries continue to resolve during any remaining transition period.
-- -----------------------------------------------------------------------------

ALTER DATABASE trading SET search_path TO tradingsystem, authentication, public;


-- ===== from migrations/004_create_watchlists_alerts_and_market_prices.sql =====
-- =============================================================================
-- Enterprise Trading Platform
-- Migration 004: Named watchlists and price alerts
-- PostgreSQL
-- =============================================================================


SET search_path TO tradingsystem, authentication, public;

-- -----------------------------------------------------------------------------
-- WATCHLISTS
-- One named watchlist per row, scoped to a single trading account.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS tradingsystem.watchlists(
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    account_id  BIGINT        NOT NULL,
    name        VARCHAR(100)  NOT NULL,
    created_on  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_on  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_watchlists PRIMARY KEY (id),

    CONSTRAINT fk_watchlists_account
        FOREIGN KEY (account_id)
        REFERENCES tradingsystem.accounts (id),

    CONSTRAINT uq_watchlists_account_name
        UNIQUE (account_id, name),

    CONSTRAINT uq_watchlists_id_account
        UNIQUE (id, account_id),

    CONSTRAINT ck_watchlists_name_non_blank
        CHECK (btrim(name) <> '')
);

-- -----------------------------------------------------------------------------
-- WATCHLIST ENTRIES
-- One symbol per watchlist, with duplicates forbidden inside a watchlist.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS tradingsystem.watchlist_entries(
    watchlist_id BIGINT       NOT NULL,
    symbol       VARCHAR(20)  NOT NULL,
    added_on     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_watchlist_entries PRIMARY KEY (watchlist_id, symbol),

    CONSTRAINT fk_watchlist_entries_watchlist
        FOREIGN KEY (watchlist_id)
        REFERENCES tradingsystem.watchlists (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_watchlist_entries_instrument
        FOREIGN KEY (symbol)
        REFERENCES tradingsystem.instruments (symbol)
);

-- -----------------------------------------------------------------------------
-- PRICE ALERTS
-- Alerts are account-scoped and independent of watchlists.
-- One-shot alerts: triggered once when threshold is met, then marked TRIGGERED.
-- Can be manually CANCELLED by the customer.
-- No watchlist_id dependency: customers can create alerts for any supported instrument.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS tradingsystem.price_alerts(
    id              BIGINT GENERATED ALWAYS AS IDENTITY,
    account_id      BIGINT         NOT NULL,
    symbol          VARCHAR(20)    NOT NULL,
    direction       VARCHAR(20)    NOT NULL,
    threshold       NUMERIC(18,2)  NOT NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    triggered_on    TIMESTAMPTZ,
    created_on      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_on      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_price_alerts PRIMARY KEY (id),

    CONSTRAINT fk_price_alerts_account
        FOREIGN KEY (account_id)
        REFERENCES tradingsystem.accounts (id),

    CONSTRAINT fk_price_alerts_instrument
        FOREIGN KEY (symbol)
        REFERENCES tradingsystem.instruments (symbol),

    CONSTRAINT ck_price_alerts_direction
        CHECK (direction IN ('ABOVE', 'BELOW')),

    CONSTRAINT ck_price_alerts_status
        CHECK (status IN ('ACTIVE', 'TRIGGERED', 'CANCELLED')),

    CONSTRAINT ck_price_alerts_threshold_positive
        CHECK (threshold > 0)
);


-- -----------------------------------------------------------------------------
-- MIGRATE LEGACY WATCHLIST DATA
-- The legacy table held one flat watchlist per account. Move it into a default
-- named watchlist called Favorites before retiring the old table.
-- -----------------------------------------------------------------------------

INSERT INTO tradingsystem.watchlists (account_id, name)
SELECT legacy.account_id, 'Favorites'
FROM (
    SELECT DISTINCT account_id
    FROM tradingsystem.watchlist
) legacy
ON CONFLICT (account_id, name) DO NOTHING;

INSERT INTO tradingsystem.watchlist_entries (watchlist_id, symbol, added_on)
SELECT
    watchlists.id,
    legacy.symbol,
    legacy.added_on
FROM tradingsystem.watchlist legacy
JOIN tradingsystem.watchlists
    ON watchlists.account_id = legacy.account_id
   AND watchlists.name = 'Favorites'
ON CONFLICT (watchlist_id, symbol) DO NOTHING;

DROP TABLE IF EXISTS tradingsystem.watchlist;

-- -----------------------------------------------------------------------------
-- INDEXES
-- -----------------------------------------------------------------------------

CREATE INDEX IF NOT EXISTS ix_watchlists_account_created
    ON tradingsystem.watchlists (account_id, created_on DESC);

CREATE INDEX IF NOT EXISTS ix_watchlist_entries_symbol
    ON tradingsystem.watchlist_entries (symbol);

-- Indexes for efficient alert querying:
-- Symbol + Status: hot path for market-data evaluator finding ACTIVE alerts by symbol
-- Account + Status: customer queries for their alert portfolio

CREATE INDEX IF NOT EXISTS ix_price_alerts_symbol_status
    ON tradingsystem.price_alerts (symbol, status);

CREATE INDEX IF NOT EXISTS ix_price_alerts_account_status
    ON tradingsystem.price_alerts (account_id, status);


-- ===== from migrations/006_create_customer_preferences.sql =====
-- Sprint 7: customer preferences service persistence

CREATE TABLE IF NOT EXISTS tradingsystem.customer_preferences (
  account_id BIGINT PRIMARY KEY,
  default_account_id BIGINT NOT NULL,
  trading_default_order_type VARCHAR(16) NOT NULL CHECK (trading_default_order_type IN ('MARKET', 'LIMIT', 'STOP')),
  trading_skip_order_confirmation BOOLEAN NOT NULL,
  trading_default_trade_quantity INTEGER NOT NULL CHECK (trading_default_trade_quantity >= 1),
  notifications_trade_fills JSONB NOT NULL,
  notifications_price_alerts JSONB NOT NULL,
  display_theme VARCHAR(16) NOT NULL CHECK (display_theme IN ('LIGHT', 'DARK', 'SYSTEM')),
  display_base_currency VARCHAR(8) NOT NULL CHECK (display_base_currency IN ('USD', 'INR')),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT fk_customer_preferences_account
    FOREIGN KEY (account_id)
    REFERENCES tradingsystem.accounts (id)
    ON DELETE CASCADE,
  CONSTRAINT fk_customer_preferences_default_account
    FOREIGN KEY (default_account_id)
    REFERENCES tradingsystem.accounts (id)
    ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_customer_preferences_default_account_id
  ON tradingsystem.customer_preferences (default_account_id);


-- ===== from migrations/007_create_ai_signals.sql =====
-- =============================================================================
-- Enterprise Trading Platform
-- Migration 007: AI Trading Signals persistence
-- PostgreSQL
-- =============================================================================

CREATE TABLE IF NOT EXISTS tradingsystem.ai_signals (
    id            BIGSERIAL PRIMARY KEY,
    symbol        VARCHAR(20)    NOT NULL,
    action        VARCHAR(10)    NOT NULL,  -- BUY | SELL | HOLD
    confidence    DECIMAL(5,4)   NOT NULL,
    reasoning     TEXT           NOT NULL,
    generated_at  TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ai_signals_symbol UNIQUE (symbol)
);

CREATE INDEX IF NOT EXISTS idx_ai_signals_symbol
    ON tradingsystem.ai_signals (symbol);

CREATE INDEX IF NOT EXISTS idx_ai_signals_generated_at
    ON tradingsystem.ai_signals (generated_at DESC);


-- ===== from migrations/009_create_notification.sql =====
-- =============================================================================
-- Enterprise Trading Platform
-- Migration 009: In-app notification inbox
-- PostgreSQL
--
-- Every notification sent to a customer is stored here so the header bell
-- can list it. Email delivery is recorded separately and stays off unless
-- the customer opts in (see 010_add_email_notification_preference.sql).
-- =============================================================================

CREATE TABLE IF NOT EXISTS tradingsystem.notification (
    id          BIGINT GENERATED ALWAYS AS IDENTITY,
    account_id  BIGINT       NOT NULL,
    user_id     UUID,
    category    VARCHAR(32)  NOT NULL,
    title       VARCHAR(255) NOT NULL,
    body        TEXT         NOT NULL,
    email_sent  BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_notification PRIMARY KEY (id),

    CONSTRAINT fk_notification_account
        FOREIGN KEY (account_id)
        REFERENCES tradingsystem.accounts (id)
        ON DELETE CASCADE,

    CONSTRAINT fk_notification_user
        FOREIGN KEY (user_id)
        REFERENCES tradingsystem.users (id)
        ON DELETE SET NULL,

    CONSTRAINT ck_notification_category
        CHECK (category IN ('ORDER_FILLED', 'PRICE_ALERT'))
);

CREATE INDEX IF NOT EXISTS idx_notification_account_created
    ON tradingsystem.notification (account_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_account_unread
    ON tradingsystem.notification (account_id)
    WHERE read_at IS NULL;


-- ===== from migrations/010_add_email_notification_preference.sql =====
-- =============================================================================
-- Enterprise Trading Platform
-- Migration 010: Email notification opt-in
-- PostgreSQL
--
-- In-app notifications are always stored. Email is not sent unless the
-- customer turns this on from Profile & Preferences.
-- =============================================================================

ALTER TABLE tradingsystem.customer_preferences
    ADD COLUMN IF NOT EXISTS email_notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE;


-- ===== from migrations/011_add_notification_symbol.sql =====
-- Store the instrument symbol beside each in-app notification so the
-- header panel can show it without parsing the message text.

ALTER TABLE tradingsystem.notification
    ADD COLUMN IF NOT EXISTS symbol VARCHAR(20);


-- ===== from migrations/012_default_account_balance.sql (schema part) =====
-- Every new account starts with 10,000 cash and buying power
-- when an insert does not supply those columns.

ALTER TABLE tradingsystem.accounts
    ALTER COLUMN cash_balance SET DEFAULT 10000.00,
    ALTER COLUMN buying_power SET DEFAULT 10000.00;


