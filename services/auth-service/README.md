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

