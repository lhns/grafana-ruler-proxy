# 0003. Reload orchestration semantics

- Status: Proposed
- Date: 2026-03-05

## Context and Problem Statement

After configuration writes, upstream reload is triggered to apply changes.
Today reload behavior is asynchronous and also periodically retried, but success/failure is not coupled to write response semantics.

This creates a tradeoff between low-latency API responses and strong application guarantees.

## Decision Drivers

- Keep write APIs responsive.
- Avoid blocking on upstream reload latency.
- Ensure eventual reload attempts even after transient failures.
- Define explicit expectations for operators and clients.

## Considered Options

1. Fire-and-forget reload plus periodic scheduled reload.
2. Synchronous reload; fail write if reload fails.
3. Buffered/outbox reload queue with acknowledgments.

## Decision Outcome

Chosen option: **1. Fire-and-forget reload plus periodic scheduled reload**.

### Current-state

- Config writes call `reloadRules`, which starts an async request and does not await completion.
- Both route modules create a recurring 5-minute reload schedule.
- Reload endpoint is `POST /-/reload`.
- Slow upstream requests are only warned via middleware logging.

### Target-state

- Keep asynchronous semantics as default behavior.
- Document write acknowledgment as "persisted locally" rather than "active upstream".
- Add structured reload outcome metrics/log fields (success, latency, failure reason).
- Add optional strict mode (future) for synchronous reload where operators need stronger consistency.

## Consequences

### Positive

- Fast write responses.
- Better resilience to transient reload failures via periodic retries.
- Simple implementation with low coupling.

### Negative

- Write success does not guarantee immediate upstream activation.
- Reload failures can remain unnoticed without stronger observability.

## Links and Evidence

### Repository evidence

- `../src/main/scala/de/lhns/alertmanager/ruler/route/PrometheusRoutes.scala`
- `../src/main/scala/de/lhns/alertmanager/ruler/route/AlertmanagerRoutes.scala`
- `../src/main/scala/de/lhns/alertmanager/ruler/route/package.scala`

### External references

- https://prometheus.io/docs/prometheus/latest/querying/api/#status
- https://grafana.com/docs/mimir/latest/references/http-api/#set-rule-group
- https://grafana.com/docs/mimir/latest/references/http-api/#set-alertmanager-configuration
