# 0004. Tenancy and authentication model

- Status: Proposed
- Date: 2026-03-05

## Context and Problem Statement

Grafana Mimir APIs define tenant-scoped behavior and typically require authentication via `X-Scope-OrgID` in multitenant mode.
This service currently behaves as a single-tenant proxy with no explicit auth or tenant enforcement in routes.

The project needs a documented posture so users know what is and is not enforced.

## Decision Drivers

- Preserve simple deployment defaults.
- Avoid accidental claims of multi-tenant correctness.
- Keep room for future tenant-aware behavior.
- Align expectations with Mimir documentation.

## Considered Options

1. Document and keep single-tenant by default.
2. Enforce `X-Scope-OrgID` immediately for all relevant endpoints.
3. Build full authN/authZ and tenant isolation now.

## Decision Outcome

Chosen option: **1. Document and keep single-tenant by default**.

### Current-state

- Route handling is namespace/path-based and repo-backed, with no tenant header checks.
- Configuration maps a single namespace/internal rule path.
- Requests are proxied without explicit tenant/auth mediation logic.

### Target-state

- Keep default single-tenant behavior explicit in docs.
- Add optional tenant-aware mode (future) that validates and propagates tenant identity.
- Treat any future multi-tenant support as a separate ADR and a compatibility-impacting change.

## Consequences

### Positive

- Keeps runtime and operational model simple.
- Avoids partial/unsafe multitenant behavior.
- Makes current behavior clear to operators.

### Negative

- Not suitable for strict multi-tenant isolation requirements.
- Users expecting Mimir tenant header semantics must add external controls.

## Links and Evidence

### Repository evidence

- `../src/main/scala/de/lhns/alertmanager/ruler/Config.scala`
- `../src/main/scala/de/lhns/alertmanager/ruler/Main.scala`
- `../src/main/scala/de/lhns/alertmanager/ruler/route/PrometheusRoutes.scala`
- `../src/main/scala/de/lhns/alertmanager/ruler/route/AlertmanagerRoutes.scala`

### External references

- https://grafana.com/docs/mimir/latest/references/http-api/#authentication
- https://grafana.com/docs/mimir/latest/operators-guide/reference-http-api/#ruler
