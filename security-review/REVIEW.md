# Combined OWASP Security Review — Sprint 10

**Date**: 2026-10-08

**Scope**: one combined OWASP Top Ten review covering the four extension services required by Sprint 10:

- Customer preferences (preferences)
- Customer notifications (notifications)
- Watchlists & price alerts (watchlists)
- Portfolio and P&L (portfolio)

Every OWASP category below carries a Finding and a Disposition. Where a category does not apply to a service it is dispositioned "Out of scope" with a reason. Where a finding differs across services the per-service detail is named explicitly.

| Category | Finding | Disposition | Residual risk |
|---|---|---|---|
| **A01 — Broken Access Control** | preferences: API exposes internal `service:` routes used only by other services (internal path) — currently reachable if called with a valid customer token. notifications: calls preferences' internal route; watchlists: calls notifications' delivery route. portfolio: reads summaries and is separate. | **Mitigated / Fixed where possible** — Preferences and Notifications: internal routes now require a service credential (mutual TLS client cert or HMAC service token) and validate `x-service` header; customer-facing endpoints require bearer JWT and ownership checks. Watchlists: uses service credential when calling notifications. | If service credentials are leaked, internal routes could be invoked — mitigation: rotate credentials, limit scope, ACLs in network. Outstanding: add short-lived service tokens and rotate schedule. Owner: DevOps. |
| **A02 — Cryptographic Failures** | All services use TLS in container orchestration and verify TLS for external APIs. Auth tokens are JWT HS256 signed (auth-service) with short TTL. Secrets stored in environment for local dev only; production expects secrets manager. | **Mitigated / Accepted** — Dev environment uses well-documented dev fixtures; production must use secrets manager (Kubernetes secret/vault). Recommend migrating to asymmetric JWT (RS256) for inter-service tokens. | Residual risk if production secrets are misconfigured. Owner (operational): Platform lead. |
| **A03 — Injection** | preferences & portfolio build SQL queries. preferences validates inputs; queries use parameterised statements. portfolio uses DB client with parameter binding. notifications and watchlists do not build SQL from user input. | **Fixed / Mitigated** — All database access now uses parameterised queries and ORM/pg client parameter binding. SQL fragments are not constructed from raw input. Input validation added to endpoints that accept user-supplied symbols/filters. | Risk remains if future endpoints concatenate user input into identifiers; code review gating required. Owner: Backend lead. |
| **A04 — Insecure Design** | System design review identified two cross-service design decisions that increase risk: (1) customer-supplied delivery destinations (notifications accepts a destination in customer data), (2) synchronous internal calls that rely on caller-provided IDs. | **Mitigated / Accepted** — (1) Notifications: customer destinations are validated against an allow-list, sanitized, and executed via an outbound proxy with strict egress ACLs; SSRF protections applied (see A10). (2) Internal call design changed to require service credential and explicit resource-scoped authorization. | Residual risk: business decisions (supporting arbitrary external destinations) increase attack surface. Where allowed, destinations are restricted and monitored. Owner: Product + Security. |
| **A05 — Security Misconfiguration** | Default configs sometimes expose extra endpoints in dev images; compose files include README-embedded dev secrets. | **Fixed / Mitigated** — Remove dev-only env values from production manifests; container images trimmed for production demo; health and admin endpoints guarded by network ACLs. CI lint rejects images with embedded secrets. | Risk remains if teams reuse dev compose in shared environments. Owner: CI maintainer. |
| **A06 — Vulnerable and Outdated Components** | Some services used dependency versions flagged by `npm audit`/`maven` warnings. | **Mitigated** — Dependencies updated where safe; documented exceptions listed with compensating controls (pinning, monitoring). Regular dependency auditing scheduled in CI. | Residual risk for transitive deps not easily upgraded. Owner: Developers. |
| **A07 — Identification and Authentication Failures** | All four services verify the platform JWT on protected routes. However, internal calls previously reused customer tokens; this allowed a customer token to reach an internal route in preferences/notifications. | **Fixed** — Internal routes now reject customer-bound JWTs and require service credentials. Public endpoints continue to require per-request token verification (signature, `accountId` claim check) and resource ownership checks. | Residual risk: token leakage. Mitigation: token lifetime short, rotate signing keys, and ensure no customer token is used for service-to-service calls. Owner: Auth/Platform. |
| **A08 — Software and Data Integrity Failures** | No automated code-signing for artifacts; deployment relies on container images built from repo. | **Mitigated** — CI now includes reproducible build steps and image hashing; production demo images are built in CI and stored in a registry with digest verification. | Acceptance relies on CI security; if CI is compromised, integrity can be broken. Owner: CI/Platform. |
| **A09 — Security Logging and Monitoring Failures** | Services log events (auth, notification deliveries, alert triggers) but some logs included too-detailed context in dev which could leak sensitive info. Centralised monitoring ingest configured in staging only. | **Mitigated** — Logging policy enforced: no tokens or secrets in logs, PII minimised. Alerts added for replay detection and repeated failed auth. Centralised log sink configured for demo environment. | Residual risk: logs retained in external systems need retention and access controls. Owner: Observability lead. |
| **A10 — Server-Side Request Forgery (SSRF) / Outbound Requests** | notifications accepts a `destination` in customer preferences used to deliver notifications. Without validation, the platform could be induced to POST to internal endpoints or arbitrary external hosts (SSRF). | **Mitigated / Partially Fixed** — Validation implemented: destinations must match an allow-list or be validated via a verification handshake (confirmation token). Outbound requests pass through a proxy that enforces egress allow-list and timeout. For destinations that cannot be verified, delivery falls back to an internal channel (email or inbox). | Residual risk: if allow-list is too permissive or proxy is misconfigured, SSRF remains possible. Owner: Notifications lead. |

---

## Outstanding items (open at Friday)

| ID | Finding | Service | Action required | Owner | Target date |
|----|---------|---------|-----------------|-------|-------------|
| 0001 | Short-lived service token rotation not automated | preferences, notifications, watchlists | Add rotation jobs and rotation policy, integrate with secrets manager | DevOps (Sam) | 2026-10-12 |
| 0002 | CI dependency scanning false-positive exceptions need review | all services | Triage and either upgrade or document compensating control | Developers (Priya) | 2026-10-11 |
| 0003 | Production secret management onboarding | all services | Migrate env secrets to vault and update manifests | Platform (Jordan) | 2026-10-12 |

Any item listed here was still open on Friday; the owner is responsible for tracking to closure and updating this file when complete.

---

## Summary

- One combined review covers all four services; where findings differ they are described per service rather than producing four stitched files.
- The two Sprint-10 specific risks were identified and addressed:
  - Internal-call reachable by customer token: fixed by requiring service credentials on internal routes and rejecting customer JWTs for service-to-service endpoints.
  - Customer-supplied destination (SSRF): mitigations include allow-listing, verification handshake, and proxyed egress with timeouts.
- Findings are a mixture of Fixed (code changes applied), Mitigated (compensating controls and operational steps), and Accepted with explicit residual risk statements where appropriate.

## Deployment checklist

- [ ] Ensure production secrets are stored in a secrets manager and rotated.
- [ ] Configure egress proxy and apply destination allow-lists for `notifications`.
- [ ] Enable CI dependency scanning and image digest locking.
- [ ] Validate health/admin endpoints are network-restricted in demo environment.
- [ ] Run end-to-end probes from `scripts/check.sh --live` to verify access control and token verification on each service.

## Approval

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Development lead | __________ | __________ | __________ |
| Security reviewer | __________ | __________ | __________ |
