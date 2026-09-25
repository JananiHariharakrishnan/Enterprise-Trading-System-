# Auth Service

NestJS auth service for the trading platform.

## Implemented routes

- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh`
- `GET /auth/me`

The contract lives in `../../contracts/auth-api.yaml`.

## Structure

- `src/app/auth/` — controller, service, DTOs, guard, decorator, token helpers, repository
- `src/app/common/` — database helper, logger, platform exception filter
- `src/main.ts` — Nest bootstrap
- `src/app.module.ts` — root Nest module

## Database tables used

- `users`
- `credentials`
- `refresh_tokens`

These are created by:

- `../../Database/migrations/001_create_trading_schema.sql`
- `../../Database/migrations/002_load_seed_data.sql`

## Security choices

- Passwords are hashed with `argon2id`
- Default Argon2 parameters are `time=3`, `memory=32768KB`, `parallelism=1`
- Refresh tokens are opaque values and only the secret hash is stored in `refresh_tokens`
- Every successful refresh rotates the token pair and issues a brand new refresh token
- Reusing an already exchanged refresh token returns `AUTH-401` and revokes the whole refresh-token session lineage
- Access tokens are signed with `HS256` using `JWT_SECRET`
- Access token claims are exactly: `sub`, `accountId`, `roles`, `iat`, `exp`, `iss`

### Logging policy

- Use only the shared `src/app/common/logger.ts` logger for app logs.
- Never pass an entire request body (for example `body`, `req.body`, or `request.body`) directly to `logger.info`, `logger.warn`, or `logger.error`.
- If request data must be logged, log explicit fields only, or call `redact(...)` first.
- `npm run check:log-safety` enforces this policy and is safe to run in CI or pre-commit hooks.

### Login Security

**Timing Attack Prevention:**
- Unknown usernames and wrong passwords return identical status (401 AUTH-401), identical error body, and comparable response timing
- When a username is not found, the service still verifies the supplied password against a fixed dummy hash (same argon2 parameters)
- This prevents attackers from using response times to enumerate valid usernames

**Login Attempt Throttle:**
- Limit: **5 failed login attempts**
- Cooldown: **60 seconds**
- Applies per IP address (or caller)
- After exceeding the limit, the endpoint returns `429 Too Many Requests` until the cooldown expires
- Note: A throttle limits how fast an attacker can use an oracle; it does not close the vulnerability. Strong passwords and real-time monitoring are also necessary

See `SECURITY_REVIEW.md` for the refresh-token revocation decision and residual-risk notes.

## Security Review

A comprehensive security review covering OWASP categories is documented in:
- `SECURITY_REVIEW_CHECKLIST.md` — Matrix of findings and dispositions for:
  - Broken Authentication
  - Broken Access Control
  - Token Leakage
  - Replay Attacks
  - Weak Secrets
  - Refresh Token Revocation

Each category includes a **Finding** (what was checked and how), a **Disposition** (accepted with rationale), and **Residual Risk** (acknowledged gaps). This document is reviewed and approved before each deployment.

## Environment variables

Database:

- `DATABASE_URL` or `DB_URL`
- or `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`

Auth:

- `JWT_SECRET`
- `ARGON2_TIME`
- `ARGON2_MEMORY_KB`
- `ARGON2_PARALLELISM`
- `REFRESH_TOKEN_BCRYPT_COST`
- `REFRESH_TOKEN_TTL_SECONDS`

## Install

```bash
npm install
```

## Run

```bash
npm run start:dev
npm run start:prod
```

### OpenAPI Documentation

The service publishes its OpenAPI document automatically, generated from the controller and DTO decorators:

- **Human-readable page (Swagger UI)**: `http://localhost:3000/docs`
- **JSON specification**: `http://localhost:3000/docs-json`

This document describes all four routes and their request/response schemas.

## Verify

```bash
npm run build
npm test
npm run test:e2e
npm run check:log-safety
```

