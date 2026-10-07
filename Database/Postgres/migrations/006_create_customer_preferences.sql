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
