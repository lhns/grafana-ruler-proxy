# 0001. API compatibility surface

- Status: Accepted
- Date: 2026-03-05

## Context and Problem Statement

This service presents a Grafana Mimir ruler-compatible API to Grafana while delegating most traffic to upstream Prometheus/VictoriaMetrics and Alertmanager.
The repository currently relies on endpoint-level behavior in route code and comments, but does not define a single compatibility contract.

Without a documented compatibility boundary, behavior can drift from both upstream specs and user expectations.

## Decision Drivers

- Preserve Grafana ruler UX for supported backends.
- Keep implementation simple and proxy-first.
- Make supported and unsupported endpoints explicit.
- Minimize accidental contract breaks.

## Considered Options

1. Full Mimir API emulation.
2. Explicit compatibility subset with pass-through fallback.
3. Pure reverse proxy with no compatibility augmentation.

## Decision Outcome

Chosen option: **2. Explicit compatibility subset with pass-through fallback**.

Implementation status: **Implemented in current repository code**.

### Current-state

- `PrometheusRoutes` intercepts and normalizes ruler/config endpoints (`/config/v1/rules/**`) and selected Prometheus endpoints (`/api/v1/status/buildinfo`, `/api/v1/rules`) while proxying all other requests.
- `AlertmanagerRoutes` intercepts `/api/v1/alerts` for config CRUD and proxies `/alertmanager/**`.
- `buildinfo` is augmented with feature flags to expose ruler and alertmanager config support.

### Scope boundary

- This ADR records the compatibility behavior that exists today.
- Future compatibility expansion or strictness changes are out of scope for this ADR and require a new ADR.

## Consequences

### Positive

- Reduces ambiguity for users and maintainers.
- Aligns code behavior to a documented public contract.
- Keeps scope bounded while still delivering Grafana ruler functionality.

### Negative

- Some Mimir features remain intentionally unsupported.
- Requires upkeep when upstream APIs evolve.

## Links and Evidence

### Repository evidence

- `../src/main/scala/de/lhns/alertmanager/ruler/route/PrometheusRoutes.scala`
- `../src/main/scala/de/lhns/alertmanager/ruler/route/AlertmanagerRoutes.scala`
- `../src/main/scala/de/lhns/alertmanager/ruler/Main.scala`
- `../README.md`

### External references

- https://grafana.com/docs/mimir/latest/operators-guide/reference-http-api/#ruler
- https://grafana.com/docs/mimir/latest/references/http-api/#list-rule-groups
- https://grafana.com/docs/mimir/latest/references/http-api/#set-rule-group
- https://grafana.com/docs/mimir/latest/references/http-api/#get-alertmanager-configuration
- https://prometheus.io/docs/prometheus/latest/querying/api/#build-information
- https://prometheus.io/docs/prometheus/latest/querying/api/#rules
