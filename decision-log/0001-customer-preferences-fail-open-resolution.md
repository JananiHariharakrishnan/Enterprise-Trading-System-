# 0001 Isolate customer preferences in a dedicated store with fail-open channel resolution

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Platform Architecture Team, SE6 Team 2 |

## Context

The customer preferences module manages account-specific configuration including default account ID, trade confirmation prompt bypass, display currency/theme, and notification channel routing. Notification delivery components require preference resolution within a strict SLA budget of 300 ms per event to prevent message backlog accumulation. Regulatory and platform privacy boundaries strictly forbid storing personally identifiable information (PII) such as email addresses or phone numbers inside preference tables. Because customer preference updates occur infrequently while resolution queries happen on every trade fill and price trigger, the read path required deterministic resilience against database timeouts.

## Options considered

| Option | For | Against |
|---|---|---|
| Dedicated `customer_preferences` PostgreSQL table with internal service resolution and fail-open default `[IN_APP]` fallback | Strictly decouples user settings from financial account schemas; eliminates PII duplication; satisfies <300 ms SLA; guarantees trade notification delivery continues if the preference service encounters transient database failure. | Introduces a separate query on notification dispatch; requires downstream services to implement fallback policy logic. |
| Embedding preference attributes directly into the core `accounts` table in the Trade API database | Zero additional query latency when loading accounts; avoids creating an extra table or lookup path. | Violates domain separation boundaries; risks table locks on financial account rows during trivial UI theme or preference updates; entangles financial ledger state with user UI preferences. |
| Streaming preference mutations over Kafka to maintain distributed in-memory caches across all services | Near-zero lookup latency without database queries on the notification critical path. | Excessive architectural overhead for low-frequency updates; eventual consistency risks routing errors; service restarts require topic replay. |

## Decision

Isolate customer preferences in a dedicated `customer_preferences` table with upsert semantics and enforce a 300 ms timeout with fail-open fallback to `[IN_APP]` delivery for internal resolution.

The decisive factor was the operational failure mode: failing open to conservative in-app notifications ensures critical execution fills are never dropped during transient preferences database spikes, whereas caching via Kafka introduced event synchronization overhead for configuration data that changes less than once a month per account.

## Consequences

This makes extending UI settings or adding notification channels possible without migrating core financial tables or endangering trade execution transactions. It makes service degradation observable because preference resolution timeouts log structured warnings while falling back safely to `[IN_APP]` channels without dropping customer alerts. The cost accepted is that calling services must maintain local fallback policies and tolerate a potential additional database roundtrip. Downstream notification components are now constrained to respect the 300 ms resolution timeout and must never persist raw customer PII inside preference records.
