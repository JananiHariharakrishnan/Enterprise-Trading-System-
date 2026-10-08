# 0003 Evaluate price alerts only on threshold crossing with atomic state transitions

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Platform Architecture Team, SE6 Team 2 |

## Context

The watchlists and price alerts module allows customers to track instruments and configure directional price threshold alerts (`ABOVE` or `BELOW`). Market quotes arrive continuously from the Fauxnance market poller across the 6-partition `market-data` Kafka topic at high volumes. Alerts must notify customers exactly once upon reaching target thresholds without spamming repeated messages while market prices remain beyond the trigger level. Because Kafka delivers messages with at-least-once semantics, poller restarts or consumer rebalances frequently replay quotes that have already been evaluated.

## Options considered

| Option | For | Against |
|---|---|---|
| Edge-triggered threshold crossing (`previousPrice` vs `currentPrice`) combined with atomic SQL status transition (`ACTIVE` to `TRIGGERED`) | Prevents repetitive notification storms during sustained market movements; conditional SQL update `WHERE status = 'ACTIVE'` guarantees at-most-once triggering under duplicate Kafka quote deliveries without distributed locking. | Requires keeping the previous price in memory per symbol; an alert configured when the price is already beyond the threshold does not trigger until price bounces back and recrosses. |
| Level-triggered evaluation with recurring cooldown intervals (e.g. trigger whenever `currentPrice >= threshold`, throttled to once every 15 minutes) | Triggers even if the transition quote was dropped or missed during consumer downtime. | Causes severe customer notification fatigue during trending market sessions; requires managing persistent cooldown timers; fails to deliver the expected one-shot alert lifecycle. |
| In-memory alert evaluation engine backed by periodic database polling batches | High throughput in-memory predicate matching without querying PostgreSQL on each quote. | High heap memory consumption; risk of lost alert state during container restarts; polling introducing multi-second evaluation lag that misses intra-second price spikes. |

## Decision

Evaluate price alerts on threshold edge crossings (`previousPrice` to `currentPrice`) and execute an atomic status transition from `ACTIVE` to `TRIGGERED` in PostgreSQL before delegating dispatch to the Notification Service.

The decisive factor was idempotency and alert hygiene: level-triggered alerts either spam the user or require arbitrary cooldown heuristics, whereas edge-crossing combined with conditional SQL row updates (`UPDATE price_alerts SET status = 'TRIGGERED' WHERE id = ? AND status = 'ACTIVE'`) guarantees at-most-once execution even when Kafka replays quotes.

## Consequences

This makes price alert execution deterministic, eliminates repetitive notification spam for traders, and makes Kafka message redelivery safe against duplicate notification dispatch. It makes testing straightforward since alert firing rules are pure functions of `(previousPrice, currentPrice, threshold)`. The cost accepted is that the market data consumer must maintain previous quote prices in memory, and customers must explicitly reset or recreate alerts once triggered. Follow-up work requires providing an alert reactivation endpoint in the REST API and indexing `price_alerts` by symbol and active status.
