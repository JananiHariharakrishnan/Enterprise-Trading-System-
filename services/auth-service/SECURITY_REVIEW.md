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

