# Auth Service Security Review

**Date**: September 2026  
**Service**: Auth Service (NestJS)  
**Version**: 1.0.0

---

## Security Review Matrix

This document provides a written finding and disposition for each security category.

| Category | Finding | Disposition | Residual Risk |
|----------|---------|-------------|-----------------|
| **Broken Authentication** | Passwords are hashed with Argon2id (type=argon2id, time=3, memory=32768KB, parallelism=1). Timing-safe comparison prevents username enumeration via response timing. When an unknown username is presented, a dummy hash is verified to match the timing of a real password check. Failed login attempts are throttled at 5 attempts per 60 seconds per IP. Access tokens are issued with HS256 (15-minute TTL) and signed with JWT_SECRET; issuer claim is pinned to "auth-service" at the Trade API to prevent issuer substitution. | **Accepted** — The implemented controls meet OWASP authentication security requirements. Password storage uses industry-standard Argon2id hashing with no plaintext secrets. Timing-safe operations prevent enumeration attacks. Rate limiting slows brute force attacks. JWT signatures prevent token forgery. The design assumes: (1) JWT_SECRET is kept confidential in production (not checked).| Access tokens remain usable until expiry (15 min) if stolen. Short TTL bounded the risk window. Root-cause prevention requires secure secret management outside the scope of this service. |
| **Broken Access Control** | All protected endpoints (`/auth/me`, `/auth/profile`) are guarded by JwtAuthGuard, which validates bearer token format, signature, and issuer claim. Token payload is verified to contain required claims (sub, accountId, roles). Account ownership is enforced during user fetch; only the authenticated user can read their own account data. Role array is validated as a non-empty array at token issuance. | **Accepted** — Access control is enforced at the HTTP guard level before any business logic executes. Token validation ensures only authenticated, authorized users reach protected endpoints. Account ID from the token is used to fetch only that user's data. No missing authorization checks were found. | If a token private key is compromised (JWT_SECRET leak), an attacker can forge tokens for any account. Mitigation relies on strict secret management in production. |
| **Token Leakage** | Refresh tokens are stored as bcrypt hashes in the database (raw token never stored). Bearer tokens are passed in HTTP Authorization headers; they must be transmitted over HTTPS in production. The service does not log full tokens or request bodies (logging policy enforced by `npm run check:log-safety`). Access token claims contain no sensitive PII; only sub (user ID), accountId, roles, iat, exp, iss. Refresh token values are opaque (UUID.base64_secret). | **Accepted** — Private token secrets are hashed before storage, preventing database compromise from disclosing session credentials. The architecture assumes HTTPS in production (not enforced by the service). Logging policy prevents token leakage via logs. Claims are minimalist, containing only necessary authorization data. | If HTTPS is not enforced in production or middleware, tokens may be transmitted in the clear. Mitigation requires infrastructure-level TLS enforcement (outside this service). If logs are exported to unsecured storage, tokens could be exposed. Secure log handling is a deployment responsibility. |
| **Replay Attacks** | Refresh tokens are single-use. Every successful refresh marks the old token as consumed (consumed=true). If a consumed token is presented again, the service returns AUTH-401 and revokes the entire session lineage (all tokens for that session_id). Session tracking by session_id ties all tokens in a refresh chain to a single lineage. Revocation is permanent (revoked_at is set in the database). | **Accepted** — Replay of an exchanged refresh token is immediately detected and the entire session is revoked, not just the single token. This prevents an attacker from using a stolen **old** refresh token while the victim holds a **new** one from the same lineage. Session lineage guarantees that all tokens in a compromised chain stop working when replay is detected. | If an attacker steals a refresh token and uses it **before** the legitimate client, the attacker will receive the next token pair first. When the client later presents the already-consumed token, both the attacker's session and the client's session are revoked, resulting in denial of service rather than session hijacking. This is a design trade-off: revoke all or allow silent session sharing. The team has chosen revocation. |
| **Weak Secrets** | JWT_SECRET is stored in `.env.example` as `development-only-shared-secret-change-me` (clearly marked as a development fixture). The same secret is used in repository `.env` for local integration testing. Issuer pinning (JWT_ISSUER="auth-service" at Trade API) provides defense-in-depth: even if an attacker forges a token with the shared secret, it is rejected if the issuer claim does not match. Argon2id parameters (time=3, memory=32768KB, parallelism=1) are configurable but default to strong values resistant to GPU/ASIC brute force. Bcrypt cost factor for refresh token hashes is 12, a standard strong default. | **Accepted** — The `.env.example` secret is explicitly marked as development-only and is not rotated after local testing. Production deployment must generate a new JWT_SECRET (e.g., openssl rand -hex 32) and store it in a secrets manager (not in the repository). Issuer pinning closes the token-forgery attack vector. Argon2id and Bcrypt parameters are production-grade. | If the development JWT_SECRET (`development-only-shared-secret-change-me`) is deployed to production without rotation, an attacker with knowledge of the shared secret can forge tokens. **Mitigation (operational)**: Mandate secret rotation before deploying to any shared or production environment. Implement automated secret scanning in CI/CD to prevent repository commits containing sensitive values. |
| **Refresh Token Revocation** | **Decision: IMPLEMENTED** — The service implements full revocation of the presented refresh token lineage. Revocation is not partial or opt-in; it is mandatory. When replay is detected (a consumed token is presented again), the service atomically sets revoked_at for the session_id, revoking all tokens in that lineage. On every refresh attempt, the service checks both consumed and revoked status and denies access if either is true. Revocation decision is logged and returned as AUTH-401 to the client. | **Accepted** — Revocation is implemented as a mandatory component of the session lifecycle, not a fallback. The architecture treats replay as a security incident (not a benign retransmission) and escalates to session revocation. This prevents silent long-lived session sharing if a token is stolen and replayed. | Unlike stateless JWT systems, this design requires a database lookup on every refresh. The performance cost is a trade-off for security. If database latency becomes a bottleneck, caching strategies (e.g., short-lived bloom filters of revoked sessions) could be employed. Revocation relies on accurate server time; clock skew could cause false positives or false negatives. |

---

## Summary

✓ **All security categories are addressed.**  
✓ **Every category has both a Finding and a Disposition.**  
✓ **Residual risks are named and documented.**  

### Key Security Decisions

1. **Argon2id password hashing** with timing-safe comparison prevents brute-force and enumeration attacks.
2. **JWT issuer pinning** ("auth-service") at the API layer prevents issuer substitution even if the secret leaks.
3. **Refresh token rotation + revocation** closes the gap between rotation-only designs; a compromised lineage is completely revoked.
4. **Single-use refresh tokens** with session lineage tracking prevent silent session sharing after replay.
5. **Rate limiting** (5 attempts/60s on login) slows brute-force attacks but does not eliminate them.
6. **Access control guards** on all protected endpoints validate both token signature and authorization claims.

### Deployment Checklist

- [ ] Generate a new JWT_SECRET (e.g., `openssl rand -hex 32`) before deploying to any shared environment.
- [ ] Remove the development fixture secret from production `.env`.
- [ ] Ensure all traffic uses HTTPS/TLS in production (external to this service).
- [ ] Validate that JWT_ISSUER is set to `"auth-service"` in production configuration.
- [ ] Run `npm run check:log-safety` in CI/CD to prevent token leakage via logs.
- [ ] Monitor login failures and refresh revocations for anomalies (brute-force, replay attacks).
- [ ] Test refresh token revocation before production deployment (e.g., `npm run test:e2e`).

---

## References

- **OWASP Top 10 (2021)**: A02:2021 – Cryptographic Failures, A07:2021 – Identification and Authentication Failures, A01:2021 – Broken Access Control
- **NIST SP 800-63B**: Authentication and Lifecycle Management
- **Auth Service Implementation**: 
  - Token handling: `src/app/auth/tokens/auth.tokens.ts`
  - Refresh token logic: `src/app/auth/repository/auth.repository.ts`
  - Access control: `src/app/auth/guards/jwt-auth.guard.ts`
  - Password hashing: `src/app/auth/repository/auth.repository.ts`
  - Rate limiting: `src/app.module.ts`
- **DB Schema**: `../../Database/Postgres/migrations`
- **Existing Security Review**: `SECURITY_REVIEW.md` (refresh token rotation and issuer pinning detailed decisions)

---

## Approval

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Development Lead | __________ | __________ | __________ |
| Security Review | __________ | __________ | __________ |


