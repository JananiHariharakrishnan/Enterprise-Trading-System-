# Auth Service

Minimal Next.js setup for the auth service.

## Requirements

- Node.js 20 or later

## Setup

Run the commands below from `services/auth-service`.

```powershell
npm ci
npm run typecheck
npm run build
npm run dev
```

For a production run:

```powershell
npm start
```

The app uses the standard Next.js app router under `src/app/`.

By default, Next.js serves on port `3000`. You can override that with the standard `PORT` environment variable.

## Database environment variables

The auth service can read either a connection string or individual Postgres fields:

- `DATABASE_URL` (or legacy `DB_URL`)
- `POSTGRES_HOST` / `POSTGRES_PORT` / `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD`
- Legacy aliases are also accepted: `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD`

If you do not provide a connection string, ensure user and password are set. Otherwise startup fails with a database configuration error.

