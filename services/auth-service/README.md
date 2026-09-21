# Auth Service

Minimal Node.js 20+ and TypeScript scaffold for the upcoming login/signup work.

## What is included

- Node 20+ compatible TypeScript service under `src/`
- Specs beside the code they cover
- Runtime config loaded from environment variables
- `npm ci`, `npm run build`, and `npm test` ready once `package-lock.json` is committed

## Local setup

1. Copy `.env.example` to `.env`
2. Set `JWT_SECRET` and database values for your machine
3. Run the commands below from `services/auth-service`

```powershell
npm ci
npm run build
npm test
npm start
```

## Current routes

- `GET /health`
- `POST /auth/login` - placeholder
- `POST /auth/signup` - placeholder

The existing `services/auth-stub` remains untouched and can continue to be used until the real login/signup story is implemented.

