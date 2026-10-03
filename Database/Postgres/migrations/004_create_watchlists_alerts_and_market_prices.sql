-- =============================================================================
-- Enterprise Trading Platform
-- Migration 004: Named watchlists and price alerts
-- PostgreSQL
-- =============================================================================

BEGIN;

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
-- Alerts are account-scoped and tied to a symbol already present in a watchlist.
-- The composite FKs enforce both account ownership and watchlist membership.
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS tradingsystem.price_alerts(
    id                      BIGINT GENERATED ALWAYS AS IDENTITY,
    account_id              BIGINT         NOT NULL,
    watchlist_id            BIGINT         NOT NULL,
    symbol                  VARCHAR(20)    NOT NULL,
    direction               VARCHAR(20)    NOT NULL,
    threshold               NUMERIC(18,2)  NOT NULL,
    status                  VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    last_condition_met      BOOLEAN        NOT NULL DEFAULT FALSE,
    last_evaluated_price    NUMERIC(18,2),
    last_quote_as_of        TIMESTAMPTZ,
    triggered_on            TIMESTAMPTZ,
    notification_status     VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    notification_attempts   INT            NOT NULL DEFAULT 0,
    last_notification_error VARCHAR(200),
    created_on              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_on              TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_price_alerts PRIMARY KEY (id),

    CONSTRAINT fk_price_alerts_watchlist_owner
        FOREIGN KEY (watchlist_id, account_id)
        REFERENCES tradingsystem.watchlists (id, account_id)
        ON DELETE CASCADE,

    CONSTRAINT fk_price_alerts_watchlist_entry
        FOREIGN KEY (watchlist_id, symbol)
        REFERENCES tradingsystem.watchlist_entries (watchlist_id, symbol)
        ON DELETE CASCADE,

    CONSTRAINT ck_price_alerts_direction
        CHECK (direction IN ('ABOVE_OR_EQUAL', 'BELOW_OR_EQUAL')),

    CONSTRAINT ck_price_alerts_status
        CHECK (status IN ('ACTIVE', 'TRIGGERED', 'DISABLED')),

    CONSTRAINT ck_price_alerts_notification_status
        CHECK (notification_status IN ('PENDING', 'SENT', 'FAILED')),

    CONSTRAINT ck_price_alerts_threshold_positive
        CHECK (threshold > 0),

    CONSTRAINT ck_price_alerts_notification_attempts_non_negative
        CHECK (notification_attempts >= 0)
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

CREATE INDEX IF NOT EXISTS ix_price_alerts_account_status
    ON tradingsystem.price_alerts (account_id, status);

CREATE INDEX IF NOT EXISTS ix_price_alerts_symbol_status
    ON tradingsystem.price_alerts (symbol, status);

CREATE INDEX IF NOT EXISTS ix_price_alerts_watchlist_id
    ON tradingsystem.price_alerts (watchlist_id);

COMMIT;

