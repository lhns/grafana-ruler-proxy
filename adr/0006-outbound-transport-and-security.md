# 0006. Outbound transport and security posture

- Status: Proposed
- Date: 2026-03-05

## Context and Problem Statement

The service configures global proxy discovery and trust manager behavior before creating outbound clients.
This is practical for enterprise networks, but it also affects TLS/proxy trust boundaries and should be explicitly governed.

## Decision Drivers

- Work in restricted corporate network environments.
- Support upstream HTTPS/proxy use cases with low friction.
- Avoid hidden global security side effects.
- Keep configuration and behavior auditable.

## Considered Options

1. Keep current global proxy/trust configuration with clear documentation.
2. Remove all proxy and trust customization.
3. Replace globals with explicit per-client transport configuration only.

## Decision Outcome

Chosen option: **1. Keep current global proxy/trust configuration with clear documentation**.

### Current-state

- Startup sets a default `ProxySelector` via Proxy Vole strategies.
- Startup sets default trust manager using environment-driven trust material.
- Outbound HTTP is performed through the JDK HTTP client and route proxy helpers.

### Target-state

- Keep current defaults for compatibility and simplicity.
- Document security implications and deployment expectations in README/ops docs.
- Prefer explicit per-client transport controls in future refactors to reduce global side effects.
- Add observability for outbound destination/reload failures without leaking sensitive details.

## Consequences

### Positive

- Better out-of-the-box behavior behind enterprise proxies.
- Allows TLS trust customization without code changes.
- Keeps dependencies and runtime model simple.

### Negative

- Global process-level configuration can have wider-than-expected impact.
- Misconfiguration may weaken outbound trust posture.

## Links and Evidence

### Repository evidence

- `../src/main/scala/de/lhns/alertmanager/ruler/Main.scala`
- `../src/main/scala/de/lhns/alertmanager/ruler/route/package.scala`
- `../build.sbt`

### External references

- https://docs.docker.com/docker-hub/builds/
- https://grafana.com/docs/mimir/latest/references/http-api/#authentication
