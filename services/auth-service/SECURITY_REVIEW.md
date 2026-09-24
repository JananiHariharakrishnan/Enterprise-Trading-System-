# Security review: refresh-token rotation and revocation

## Decision

The service **implements revocation of the presented refresh token lineage**.

## Implemented behaviour

- The refresh token stored in PostgreSQL is **not the raw token**. The service stores only a **bcrypt hash of the opaque secret** in `refresh_tokens.token_hash`.
- Every successful `POST /auth/refresh` call:
  - validates the presented token,
  - marks that token as consumed,
  - issues a new access token,
  - issues a brand new refresh token,
  - stores the new refresh token as the replacement in the same `session_id` lineage.
- If a refresh token that has already been exchanged is presented again, the service returns **`AUTH-401`**.
- When replay of an exchanged refresh token is detected, the service **revokes all refresh tokens in the same session lineage** by setting `revoked_at` for that `session_id`.

## Why this decision was chosen

Rotation alone limits the value of a stolen refresh token to a single successful use, but it still leaves both the original holder and the thief with live sessions if the old token value continues to work.

The implemented replay handling closes that gap:

- the old refresh token cannot be used twice,
- replay is treated as suspicious,
- the active refresh-token lineage is revoked so the service does not continue two concurrent refresh-capable sessions after replay is observed.

## Residual risk

- A stolen **access token** remains usable until its expiry time; this is bounded by the short access-token lifetime.
- If an attacker steals a live refresh token and uses it **before** the legitimate client, the attacker may obtain the next token pair first. When the legitimate client later presents the already exchanged token, the service revokes the whole lineage, which contains the attacker too. The result is denial of service for that session rather than silent long-lived session sharing.
- Read-only database compromise does not by itself become session takeover through refresh tokens, because only the hashed secret is stored.

## Acceptance-criteria mapping

- Every refresh issues a new refresh token and the new one works.
- A second presentation of an already exchanged refresh token returns `AUTH-401`.
- The implemented policy is: **rotate on every refresh, revoke the full session lineage on replay**.

---

# Security review: JWT_SECRET and issuer pin management

## Decision

The service **keeps the published development fixture `JWT_SECRET` unchanged** in `.env.example` and repository-root `.env` for local integration testing. This is a **deliberate development practice**, not a production secret.

The platform **pins the issuer claim** to `"auth-service"` at the Trade API to prevent acceptance of tokens from other issuers (e.g., the old `auth-stub` test fixture or an attacker-controlled service).

## Rationale

- **Development convenience**: The published `development-only-shared-secret-change-me` allows all local services to start with a working configuration without manual secret exchange. The comment in `.env.example` makes the fixture status explicit.
- **Issuer pinning prevents issuer substitution**: Even if an attacker can forge a token with the shared secret, the Trade API rejects it if the `iss` claim is not `"auth-service"`. This prevents:
  - Accidental acceptance of tokens from the old Sprint 6 `auth-stub` test fixture
  - Cross-service token confusion
  - Issuer substitution attacks in shared-secret scenarios
- **Token contract is issuer + signature**: The Trade API verifies both the HMAC-SHA256 signature (via `JWT_SECRET`) and the issuer claim (via `JWT_ISSUER`). Both must match; either alone is insufficient.

## Implementation details

- Auth service signs all access tokens with `issuer: "auth-service"` in the payload.
- Trade API's `JwtTokenVerifier` is instantiated with the expected issuer and validates the `iss` claim before accepting the token.
- Trade API's `TemporarySecurityConfig` reads `JWT_ISSUER` from the environment, defaulting to `"auth-stub"` only for backward compatibility with existing test code. It must be set to `"auth-service"` in production or integration test configurations.
- All integration tests explicitly set `JWT_ISSUER=auth-service`.

## Production considerations

In production:

1. Generate a new, strong `JWT_SECRET` (e.g., via `openssl rand -hex 32`) and store it in a secrets manager (not in `.env.example`).
2. Rotate the `JWT_SECRET` if it is ever compromised or exposed (e.g., via logs, version control history, or accidental commits).
3. The issuer claim `"auth-service"` is a logical identifier and does not need to be rotated; it is a service name, not a secret.
4. The `.env.example` placeholder `development-only-shared-secret-change-me` must be replaced before deploying to any shared environment.

## Acceptance-criteria mapping

- ✓ Trade REST API reads the same `JWT_SECRET` the auth service signs with, passed from root `.env` through docker-compose.
- ✓ Trade REST API's issuer setting points to `"auth-service"` via the `JWT_ISSUER` environment variable.
- ✓ A token signed with an untrusted key (e.g., `UNTRUSTED_SECRET`) is refused (signature verification fails).
- ✓ A token signed with the correct key but a wrong issuer (e.g., `"auth-stub"` or `"attacker"`) is refused (issuer claim validation fails).
- ✓ Integration test confirms refusal with no token, with an invalid token, with a token from a wrong issuer, and with a token signed by an untrusted key.

