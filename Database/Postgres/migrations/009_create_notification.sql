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
