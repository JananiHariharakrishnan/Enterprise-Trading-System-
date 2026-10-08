# 0004 Price portfolios from in-memory quote cache and calculate realised PnL via fill replay

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Platform Architecture Team, SE6 Team 2 |

## Context

The portfolio and P&L module provides account valuations, unrealised gains, and historical realised P&L across multiple currencies (USD, INR) for customer dashboards. External quotes from the Fauxnance market API are subject to strict rate limits and quota allocations, making synchronous third-party HTTP calls during portfolio views unsustainable under multi-user traffic. Platform accounting rules require realised P&L to be booked at the exact instant of each sell order using the weighted-average cost basis of the position at that time. Completed trade fills are persisted in the trading database, while live quotes are continuously broadcast over the 6-partition `market-data` Kafka topic.

## Options considered

| Option | For | Against |
|---|---|---|
| Price holdings against an in-memory quote cache populated from `market-data` and compute realised P&L on demand via chronological fill replay | Zero external Fauxnance HTTP requests during user portfolio queries; zero quota consumption; sub-10 ms response times; mathematical guarantee that realised P&L precisely reflects the Trade Executor weighted-average cost rule without maintaining synchronized ledger tables. | Quotes older than 300 seconds must be flagged as stale and partial portfolio summaries returned if quotes are absent; calculating realised P&L requires reading historical fills for the account. |
| Query Fauxnance REST endpoints synchronously on every portfolio read request | Guarantees up-to-the-second market prices without maintaining local quote caches. | Quickly exhausts external API quota under concurrent user traffic; adds 300 to 1500 ms latency to page rendering; leaves the portfolio service vulnerable to external provider outages. |
| Materialize realised P&L into a dedicated ledger table updated during each trade execution transaction | Enables instant aggregate P&L reads via simple SQL queries without replaying fills. | Couples Trade Executor transactions to reporting ledger updates; increases write transaction contention; schema migrations or historical trade adjustments risk data divergence between ledger and fill records. |

## Decision

Price portfolio holdings exclusively from the in-memory quote cache populated by the `market-data` Kafka consumer and compute realised P&L dynamically by replaying historical fills in chronological order.

The decisive factor was quota preservation and audit consistency: querying Fauxnance synchronously would exhaust API rate limits within minutes of active user trading, whereas in-memory quote evaluation consumes zero quota and fill replay guarantees bit-for-bit accounting consistency with the Trade Executor's weighted-average cost formula without risking distributed ledger drift.

## Consequences

This makes portfolio views performant (sub-10 ms) and completely resilient to external Fauxnance API downtime. It makes the portfolio read path strictly non-blocking and cost-free in terms of external API quota. The cost accepted is that holdings without fresh quotes within 300 seconds are marked as stale, resulting in partial portfolio valuations until new quotes arrive on the Kafka bus. It constrains historical fill records in PostgreSQL to remain indexed by `account_id` and `executed_at` so replay latency remains negligible as transaction volumes grow.
