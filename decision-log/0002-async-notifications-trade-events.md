# 0002 Dispatch customer notifications asynchronously by consuming trade lifecycle events

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-06 |
| Decided by | Platform Architecture Team, SE6 Team 2 |

## Context

The customer notifications module delivers trade execution notices (fills, cancellations, rejections) and price threshold alerts across in-app and email channels. Orders are processed across the 3-partition `orders` queue by the Trade Executor, which publishes terminal status events onto the `trade-events` Kafka topic. Order placement and execution have sub-second latency targets and must never stall due to external SMTP server latency or third-party email provider outages. The platform event bus specification mandates that services with side effects run independent consumer groups with post-processing offset commits.

## Options considered

| Option | For | Against |
|---|---|---|
| Asynchronous consumption of `trade-events` using dedicated Kafka consumer group `notification-service` | Completely decouples core order execution throughput from outbound delivery latency; SMTP network timeouts never stall trade processing; independent offsets prevent lag propagation to portfolio or analytics consumers. | Introduces asynchronous delivery latency (typically 50-200 ms); consumer must implement idempotent event handling to guard against duplicate notifications upon consumer group rebalances. |
| Synchronous notification dispatch within the Trade Executor execution pipeline | Immediate delivery triggered synchronously with database fill transactions; eliminates Kafka consumer infrastructure for notifications. | Outbound email delivery times (often 500-2000 ms) directly degrade trading engine throughput; third-party email failure risks aborting or delaying committed financial transactions. |
| Dual-publishing separate notification events to an independent `notifications` Kafka topic from the Trade Executor | Keeps notification payload formats distinct from raw financial execution events. | Requires dual-write coordination inside the executor; risks inconsistent state if publishing to `trade-events` succeeds but `notifications` fails. |

## Decision

Consume terminal lifecycle messages directly from the `trade-events` Kafka topic using a dedicated consumer group `notification-service` and dispatch notifications asynchronously according to resolved customer preferences.

The decisive factor was isolating execution throughput from outbound delivery latency: SMTP network pauses of 1 to 2 seconds would stall order processing partitions in the Trade Executor, whereas consuming from `trade-events` guarantees execution completes unconditionally and fails in an observable queue without endangering financial transactions.

## Consequences

This makes trade execution performance and throughput immune to network latency, throttling, or downtime in email delivery providers. It makes modifying notification templates or adding new delivery channels possible without deploying changes to the Trade Executor. The cost accepted is that customer notifications are eventually consistent rather than instantaneous, and the notification listener must handle duplicate event replays safely using manual offset acknowledgments. Any future trade lifecycle outcome requiring customer alerting is now constrained to be published onto `trade-events` with a valid `eventId` and `accountId`.
