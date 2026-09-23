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
- Default Argon2 time cost is `12`
- Refresh tokens are opaque values and only the secret hash is stored in `refresh_tokens`
- Every successful refresh rotates the token pair and issues a brand new refresh token
- Reusing an already exchanged refresh token returns `AUTH-401` and revokes the whole refresh-token session lineage
- Access tokens are signed with `HS256` using `JWT_SECRET`
- Access token claims are exactly: `sub`, `accountId`, `roles`, `iat`, `exp`, `iss`

See `SECURITY_REVIEW.md` for the refresh-token revocation decision and residual-risk notes.

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

Swagger is served at `/docs`.

## Verify

```bash
npm run build
npm test
npm run test:e2e
```

