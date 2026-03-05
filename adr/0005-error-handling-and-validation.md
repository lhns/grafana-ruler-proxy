# 0005. Error handling and validation policy

- Status: Proposed
- Date: 2026-03-05

## Context and Problem Statement

Current config parsing/serialization paths rely on `toTry.get`, `unsafeFromString`, and direct YAML parse assumptions in multiple places.
This can turn malformed input or file content into unstructured runtime failures instead of clear API errors.

The service should define what failures are expected to be client-visible validation errors vs internal server errors.

## Decision Drivers

- Maintain operator trust with predictable failures.
- Return contract-consistent HTTP responses for bad input.
- Reduce crash risk from malformed config.
- Keep implementation proportionate to project size.

## Considered Options

1. Keep fail-fast unsafe parsing.
2. Introduce structured validation and typed error mapping.
3. Add permissive best-effort parsing with partial writes.

## Decision Outcome

Chosen option: **2. Introduce structured validation and typed error mapping**.

### Current-state

- YAML decode failures may throw during POST handling.
- File parse errors may throw on read paths.
- `Config` codec uses unsafe URI and partial decoders.

### Target-state

- Replace unsafe parse/get paths with explicit `Either`/`Validated` handling.
- Map malformed payloads to client errors (4xx) with clear messages.
- Map file I/O and upstream failures to server errors (5xx) with structured logs.
- Preserve existing endpoint behavior and payload shape where possible.

## Consequences

### Positive

- More predictable API behavior under invalid input.
- Better debuggability and safer operations.
- Lower chance of process-level failures from bad config data.

### Negative

- Additional code complexity in route/repo layers.
- Requires careful compatibility checks for response status/body changes.

## Links and Evidence

### Repository evidence

- `../src/main/scala/de/lhns/alertmanager/ruler/Config.scala`
- `../src/main/scala/de/lhns/alertmanager/ruler/route/package.scala`
- `../src/main/scala/de/lhns/alertmanager/ruler/route/AlertmanagerRoutes.scala`
- `../src/main/scala/de/lhns/alertmanager/ruler/repo/RulesConfigRepoFileImpl.scala`
- `../src/main/scala/de/lhns/alertmanager/ruler/repo/AlertmanagerConfigRepoFileImpl.scala`

### External references

- https://grafana.com/docs/mimir/latest/references/http-api/#set-rule-group
- https://grafana.com/docs/mimir/latest/references/http-api/#set-alertmanager-configuration
- https://prometheus.io/docs/prometheus/latest/querying/api/#format-overview
