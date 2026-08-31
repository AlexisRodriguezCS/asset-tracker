# 0004 — asset-service owns live custody; assignment-service owns history

**Status:** accepted

## Context

"Who holds asset 40 right now?" and "what is asset 40's full history?" are
different questions with different access patterns and different consistency
needs.

## Decision

- **asset-service** stores each asset's *current* `status` + `holder` and exposes
  guarded transitions (`assignTo`, `returnToStock`, `setStatus`). Trying to
  assign an already-assigned asset throws to **409**; a retired or lost one to
  **422**. This is the single source of truth for "what is where".
- **assignment-service** stores the *history*: append-only `Assignment` rows, one
  open row per asset, a return stamps `returnedAt`. It never writes custody state
  directly — it calls asset-service.

## Consequences

- The interesting failure paths live in one place (asset-service's guards) and
  the orchestrator just propagates them.
- "What is on desk 14" and "all laptops" are one query against asset-service's
  current state; "asset 40's timeline" is one query against assignment-service.
- The two can drift if a call partially fails — accepted for v1; a reconciliation
  job is a possible addition.
