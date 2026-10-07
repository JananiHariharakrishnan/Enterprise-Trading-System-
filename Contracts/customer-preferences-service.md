# Customer Preferences service design notes

## Purpose

This service is the single source of truth for customer preference data:
- defaultAccountId
- trading preferences
- notification channel preferences
- display settings

It does not own account master data or broad personal profile data.

## API scope

- Customer API
  - GET /api/v1/accounts/{accountId}/preferences
  - PUT /api/v1/accounts/{accountId}/preferences
- Internal notification API
  - GET /internal/v1/accounts/{accountId}/notifications/resolve?eventType=...

See contract: Contracts/customer-preferences-api.yaml

## Access control and auth

- Validate bearer JWT in this service.
- Customer routes require accountId claim to equal path accountId.
- Internal resolve route must use service credential auth and must reject customer tokens.

## Persistence

- Preference record must be persisted in database and survive service restart.
- One active preference record per accountId.
- Upsert semantics for PUT.

Suggested table shape:
- account_id BIGINT PRIMARY KEY
- default_account_id BIGINT NOT NULL
- trading_default_order_type VARCHAR NOT NULL
- trading_skip_order_confirmation BOOLEAN NOT NULL
- trading_default_trade_quantity INTEGER NOT NULL
- notifications_trade_fills JSONB NOT NULL
- notifications_price_alerts JSONB NOT NULL
- display_theme VARCHAR NOT NULL
- display_base_currency VARCHAR NOT NULL
- updated_at TIMESTAMPTZ NOT NULL

## Validation rules

- defaultAccountId >= 1
- trading.defaultOrderType in MARKET, LIMIT, STOP
- trading.defaultTradeQuantity >= 1
- notifications.tradeFills and notifications.priceAlerts are non-empty unique arrays of IN_APP, EMAIL, SMS
- display.theme in LIGHT, DARK, SYSTEM
- display.baseCurrency in USD, INR

## Personal-data ownership decision

- Do not duplicate account holder data from trade-api or auth-service.
- For notification routing targets, prefer opaque references (for example contact-ref IDs) or encrypted values.
- Never log raw contact values.

## Timeout and fallback behaviour decisions

Notification caller policy should be explicit and tested:
- Timeout budget: 300 ms to preferences resolve endpoint.
- On timeout/unavailable response:
  - fail-open option: send using safe default channels.
  - fail-closed option: do not send and surface operational alert.

Recommended for trading notifications: fail-open with conservative defaults [IN_APP].

## Default behaviour decisions

When no preference exists for an account:
- trading.defaultOrderType = MARKET
- trading.skipOrderConfirmation = false
- trading.defaultTradeQuantity = 1
- notifications.tradeFills = [IN_APP]
- notifications.priceAlerts = [IN_APP]
- display.theme = SYSTEM
- display.baseCurrency = USD
- defaultAccountId = accountId

## Angular integration

- Settings screen reads and writes this service directly.
- Sign-in flow fetches preferences and applies defaultAccountId.
- If preferences read fails at sign-in, UI falls back to token accountId and logs a client warning.

## Testing checklist

- Contract validation
  - Request schema validation for PUT
  - Response schema validation for GET and resolve
- Authentication
  - Missing token -> AUTH-401
  - Invalid/expired token -> AUTH-401
  - Internal endpoint rejects customer token -> AUTH-401
- Authorization
  - accountId mismatch between JWT and path -> ACC-403
- Persistence
  - PUT persists values
  - GET returns persisted values after service restart
- Defaults
  - Missing record returns default policy on internal resolve route
- Notification integration
  - Notifications service resolves trade fill channels
  - Notifications service resolves price alert channels
  - Timeout fallback path behaves as chosen policy
- Error envelope
  - Every non-2xx response shape is { errorCode, message }

## Out of scope in this sprint

- Preference history and rollback
- Multiple contact targets per channel
- Kafka events for preference changes
