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
