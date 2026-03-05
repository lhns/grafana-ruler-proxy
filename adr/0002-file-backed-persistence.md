# 0002. File-backed persistence for rules and alertmanager config

- Status: Proposed
- Date: 2026-03-05

## Context and Problem Statement

The proxy persists rule groups and alertmanager configuration using local files.
This keeps deployment lightweight, but raises questions about durability, multi-instance behavior, and compatibility with richer tenant/object-store workflows described by Mimir.

## Decision Drivers

- Simplicity and low operational overhead.
- Compatibility with Prometheus/VictoriaMetrics file-based workflows.
- Predictable behavior for single-instance deployments.
- Clear path to future pluggable storage if needed.

## Considered Options

1. Keep file-backed storage as the baseline.
2. Immediately adopt external shared storage.
3. Persist nothing and proxy all writes upstream.

## Decision Outcome

Chosen option: **1. Keep file-backed storage as the baseline**.

### Current-state

- Rule groups are loaded from and written to one YAML file via `RulesConfigRepoFileImpl`.
- Alertmanager config is loaded from and written to one file via `AlertmanagerConfigRepoFileImpl`.
- In-process synchronization uses a `Semaphore(1)` per repository instance.
- Alertmanager `template_files` are not yet implemented in file persistence.

### Target-state

- Keep file-backed persistence as default for single-instance deployments.
- Document operational constraint: replicas require a shared writable volume or a future shared backend.
- Introduce a pluggable repository implementation boundary if HA or multi-tenant storage is required.
- Add explicit support for `template_files` when alertmanager parity is required.

## Consequences

### Positive

- Minimal dependencies and straightforward operations.
- Works naturally with mounted config volumes.
- Maintains clear ownership of configuration state in this service.

### Negative

- Not inherently safe for multi-replica deployments without shared storage.
- File corruption/partial-write safeguards are limited.
- Some Mimir object-storage assumptions do not apply.

## Links and Evidence

### Repository evidence

- `../src/main/scala/de/lhns/alertmanager/ruler/repo/RulesConfigRepoFileImpl.scala`
- `../src/main/scala/de/lhns/alertmanager/ruler/repo/AlertmanagerConfigRepoFileImpl.scala`
- `../src/main/scala/de/lhns/alertmanager/ruler/repo/RulesConfigRepo.scala`
- `../src/main/scala/de/lhns/alertmanager/ruler/repo/AlertmanagerConfigRepo.scala`
- `../src/main/scala/de/lhns/alertmanager/ruler/Config.scala`

### External references

- https://grafana.com/docs/mimir/latest/references/http-api/#list-rule-groups
- https://grafana.com/docs/mimir/latest/references/http-api/#set-rule-group
- https://grafana.com/docs/mimir/latest/references/http-api/#set-alertmanager-configuration
