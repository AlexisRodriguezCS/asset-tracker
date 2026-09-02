# 0009 — Resilience4j retry + circuit breaker on the asset-service call

**Status:** accepted · a resilience follow-up named in [0003](0003-synchronous-orchestration.md)

## Context

`assignment-service` calls `asset-service` synchronously for every check-out /
check-in / offboard. With only a 2s / 3s timeout, a slow or flapping
`asset-service` turned every orchestration into a stuck request.

## Decision

Wrap the `AssetClient` calls (`assign`, `returnToStock`, `assetsHeldByPerson`)
with Resilience4j via `spring-cloud-starter-circuitbreaker-resilience4j`:

- **Retry** `asset-service`: 3 attempts, 300 ms apart, only on transport failures
  (`ResourceAccessException`, `HttpServerErrorException`).
- **Circuit breaker** `asset-service`: 10-call window, opens at a 60% failure
  rate, 10s open, 3 trial calls half-open.
- **409 / 422 are ignored** by both — they are business outcomes
  (`AssetUnavailableException` / `AssetNotMovableException`), not failures, so
  they neither retry nor count toward the circuit.
- When retries are exhausted or the circuit is open, the fallback raises
  `AssetServiceUnavailableException` → **HTTP 503**, instead of a hung call.
- `deployedAssets` (seed-time only) is left outside the breaker — `AssignmentSeeder`
  has its own long retry loop.

State is visible at `/actuator/circuitbreakers` and in the health endpoint.

## Consequences

- A transient blip is absorbed by retry; a real outage fails fast with a clear
  503 and recovers automatically when `asset-service` comes back.
- Business rejections still surface unchanged as 409 / 422.
- Saga-style compensation for a half-finished offboarding sweep is still open.
