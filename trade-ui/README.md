# Trading Platform UI

Angular 21 frontend for the trading platform, built with standalone components and reactive forms.

## Setup

First-time setup and dependency management:

```bash
npm ci                   # Install dependencies (use ci, not install)
npm run generate:contracts  # Generate API clients from contracts
npm start                # Start development server
```

## Development

### Start the development server

```bash
npm start                # or: ng serve
```

Navigate to `http://localhost:4200/`. The app will auto-reload on file changes.

### Build the application

```bash
npm run build            # or: ng build
```

Artifacts are written to the `dist/` directory.

### Run unit tests

```bash
npm test                 # or: ng test
```

Tests run in watch mode with [Vitest](https://vitest.dev/).

### Run end-to-end tests

```bash
npm run e2e              # Headless Playwright test run
npm run e2e:headed       # Headed (visible browser) run
```

All credentials and URLs read from environment variables. See **Environment Variables** below.

## Architecture

### Standalone Components

All components are standalone. No NgModules, anywhere. The imports at the component level are explicit.

```typescript
@Component({
  selector: 'app-example',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './example.component.html'
})
```

### API Clients

Typed clients are generated from two contracts:

- **Auth API**: `src/app/generated/auth/` — generated from `contracts/auth-api.yaml`
- **Trade API**: `src/app/generated/trade/` — generated from `trade-api/src/main/resources/trade-api.yaml`

The generator and version are pinned. Regenerate on every contract change:

```bash
npm run generate:contracts
```

See [src/app/generated/README.md](src/app/generated/README.md) for details.

### Authentication

The `AuthSessionService` manages sign-in, sign-out, and the current user:

```typescript
async login(username: string, password: string): Promise<void>
async register(username: string, password: string, accountId: number): Promise<void>
async logout(): Promise<void>
async loadCurrentUser(): Promise<UserResponse | null>
```

Tokens are stored in `sessionStorage` (cleared on tab close).

### HTTP Token Interceptor

One functional interceptor, registered once in the platform auth middleware, attaches the bearer token to requests:

- ✅ Attached to: Trade API (`http://localhost:8080/api/v1/**`), Auth service profile endpoint
- ✅ NOT attached to: `/auth/login`, `/auth/register`, `/auth/refresh`, third-party origins

The interceptor uses an allow-list of origins, not a deny-list. It fails closed if a third-party is added without explicit configuration.

See [src/app/services/http-token-interceptor.spec.ts](src/app/services/http-token-interceptor.spec.ts) for the two critical test cases:

1. **Attach case**: Request to platform API carries the header
2. **Do-not-attach case**: Request to third-party origin does not

### Route Guards

Two guards protect routes:

- **`authGuard`**: Requires valid session; redirects to `/login` if missing
- **`loginRedirectGuard`**: Redirects authenticated users away from `/login` and `/register` to `/dashboard`

The sign-in route is the only one an unauthenticated visitor may reach. Others carry the redirect address:

```typescript
// If visitor tries /dashboard while signed out, redirected to /login with return address
```

## Pages

### Home (`/`)

Landing page. Shows sign-in and register buttons for unauthenticated visitors, dashboard link for authenticated users.

### Sign In (`/login`)

Sign-in form with:
- Username field (`data-testid="username-input"`)
- Password field (`data-testid="password-input"`)
- Submit button (`data-testid="sign-in-button"`)
- Validation errors
- API error rendering (readable messages from the Auth service)

**Test data (from your setup)**:
```bash
E2E_USERNAME=<user>
E2E_PASSWORD=<password>
```

### Register (`/register`)

Registration form with:
- Username field (3–64 characters, alphanumeric + dot, dash, underscore)
- Account ID field (positive integer)
- Password field (12–128 characters)
- Confirm password field
- Cross-field validation (password match)
- Link to sign-in

### Dashboard (`/dashboard`)

Protected route. Shows account summary, positions, and recent orders. Requires valid authentication.

## Environment Variables

Playwright tests and the dev server read these:

| Variable | Default | Purpose |
|---|---|---|
| `E2E_BASE_URL` | `http://localhost:4200` | Application URL for tests |
| `E2E_AUTH_API` | `http://localhost:3000` | Auth service URL |
| `E2E_TRADE_API` | `http://localhost:8080` | Trade API URL |
| `E2E_USERNAME` | Required | Valid test username |
| `E2E_PASSWORD` | Required | Valid test password |
| `E2E_ACCOUNT_ID` | `1` | Account ID for registration tests |
| `E2E_SYMBOL` | `AAPL` | Test symbol for order placement |

Example `.env` for local testing:

```bash
E2E_BASE_URL=http://localhost:4200
E2E_AUTH_API=http://localhost:3000
E2E_TRADE_API=http://localhost:8080
E2E_USERNAME=priya.menon
E2E_PASSWORD=correct horse battery staple
E2E_ACCOUNT_ID=100
E2E_SYMBOL=AAPL
```

## File Structure

```
src/
  app/
    components/
      app-header/            # Header with sign-out button
    generated/
      auth/                  # Auto-generated Auth API client
      trade/                 # Auto-generated Trade API client
    guards/
      auth.guard.ts          # Protects guarded routes
      login-redirect.guard.ts # Redirects signed-in users away from /login
    pages/
      home/                  # Home / landing page
      login/                 # Sign-in page
      register/              # Registration page
      dashboard/             # Account dashboard (protected)
    services/
      auth-session.service.ts               # Sign-in, sign-out, user state
      auth-api-client.service.ts            # Auth API integration
      trade-api-client.service.ts           # Trade API integration
      token-storage.service.ts              # Token persistence
      http-token-interceptor.middleware.ts  # Bearer token attachment
      platform-auth-middleware.provider.ts  # Interceptor registration
    app.routes.ts            # Route definitions
    app.ts                   # Root component
  main.ts                    # Bootstrap
e2e/
  login.spec.ts              # Sign-in journey
  register.spec.ts           # Registration journey
```

## Security Notes

### No API Keys or Secrets in the Bundle

`npm run build` produces files every browser downloading the app receives. Three things are forbidden:

| Pattern | Why |
|---|---|
| API keys (`api-key`, `x-api-key`, `api_key`) | Published keys are compromised |
| Third-party host URLs | The app never calls third-party APIs directly |
| Signing secrets or JWT secrets | Keys in the browser let any reader mint tokens |

Check the built bundle before review:

```bash
grep -r "x-api-key\|api-key\|api_key\|fauxnance\|jwt_secret" dist/
```

### Token Handling

Tokens are stored in `sessionStorage`, which is cleared on tab/browser close. The bearer token is attached only to platform APIs (Auth service and Trade API), never to third-party origins.

## Testing

### Unit Tests

Vitest runs component and service tests:

```bash
npm test
```

All `*.spec.ts` files are run. Tests mock the `AuthSessionService` and `Router` to isolate components.

Test identifiers (`data-testid`) are required on:
- Username input: `data-testid="username-input"`
- Password input: `data-testid="password-input"`
- Sign-in button: `data-testid="sign-in-button"`
- Error messages: `data-testid="auth-error-invalid-credentials"`, `data-testid="auth-error-empty-field"`
- Register form fields and button: `data-testid="register-*"`

### End-to-End Tests (Playwright)

Two journeys in `e2e/`:

1. **`login.spec.ts`**: Guard redirect, refused sign-in, successful sign-in, arrival at return address, sign-out
2. **`register.spec.ts`**: Validation before submission, successful registration, duplicate username error

Run one file at a time to verify isolation:

```bash
npx playwright test e2e/login.spec.ts
npx playwright test e2e/register.spec.ts
```

Each test starts from a clean sign-in. No shared state between files.

## Deployment Checklist

- [ ] `npm ci && npm run build` succeeds on a clean machine
- [ ] `npm test` passes
- [ ] `npm run e2e` passes with real services up
- [ ] Bearer token is attached to platform APIs in the network tab
- [ ] Bearer token is NOT attached to third-party origins
- [ ] All eight catalogue error codes render as readable messages
- [ ] Built bundle contains no API keys or secrets: `grep -r "api.key\|jwt" dist/`
- [ ] Sign-in redirects to dashboard, sign-out redirects to sign-in
- [ ] Order at `NEW` status refreshes on an interval; does not re-post

## References

- [Angular 21 Documentation](https://angular.io/docs)
- [Standalone Components Guide](https://angular.io/guide/standalone-components)
- [Signals API](https://angular.io/guide/signals)
- [Reactive Forms](https://angular.io/guide/reactive-forms)
- [Vitest Documentation](https://vitest.dev/)
- [Playwright Documentation](https://playwright.dev/)


- Access and refresh tokens are stored in `sessionStorage`.
- Sign-out is available from the top header and clears session state.
- Stable test identifiers are provided for the auth journey (`username-input`, `password-input`, `sign-in-button`, `sign-out-button`).

## Running end-to-end tests

Playwright tests are configured in this project and can boot both the UI and Auth service.

Install browser binaries once:

```bash
npx playwright install chromium
```

Run e2e tests:

```bash
npm run e2e
```

Run headed mode:

```bash
npm run e2e:headed
```

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
