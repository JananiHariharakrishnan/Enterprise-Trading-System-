-- =============================================================================
-- Migration V5: Create ai_signals table for LLM-generated trading signals
-- =============================================================================

CREATE TABLE IF NOT EXISTS tradingsystem.ai_signals (
    id            BIGSERIAL PRIMARY KEY,
    symbol        VARCHAR(20)    NOT NULL,
    action        VARCHAR(10)    NOT NULL,  -- BUY | SELL | HOLD
    confidence    DECIMAL(5,4)   NOT NULL,
    reasoning     TEXT           NOT NULL,
    generated_at  TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ai_signals_symbol UNIQUE (symbol)
);

CREATE INDEX IF NOT EXISTS idx_ai_signals_symbol ON tradingsystem.ai_signals (symbol);
CREATE INDEX IF NOT EXISTS idx_ai_signals_generated_at ON tradingsystem.ai_signals (generated_at DESC);
