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
