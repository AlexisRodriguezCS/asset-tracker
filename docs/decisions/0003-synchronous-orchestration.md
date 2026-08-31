# 0003 — Synchronous orchestration + independent transactions

**Status:** accepted

## Context

Checking an asset out touches two services: asset-service (flip custody) and
notification-service (record the event). This could be a broker choreography or a
synchronous orchestrator.

## Decision

`assignment-service` orchestrates synchronously over HTTP. `POST /assignments` is
**not** `@Transactional`. Persistence is split into the short independent
transactions of `AssignmentTransactions` (`open`, `close`, reads). The
notification call is fire-and-forget; the offboarding sweep is best-effort per
asset.

## Consequences

- The flow is observable end to end — easy to demo, easy to reason about.
- A DB connection is never held open across an HTTP call, and a downstream
  failure still leaves a correct history row (the write that already committed
  stays committed).
- No automatic compensation if an offboarding sweep half-fails; the result lists
  the assets still outstanding for a human. Retries, circuit breakers and
  saga-style compensation are the resilience follow-ups.
- A broker (choreography) is the documented phase-2 alternative.
