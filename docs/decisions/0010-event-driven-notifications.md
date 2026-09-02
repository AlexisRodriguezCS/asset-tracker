# 0010 — Event-driven notifications over RabbitMQ

**Status:** accepted · the broker phase-2 alternative from [0003](0003-synchronous-orchestration.md)

## Context

`assignment-service` recorded notifications by calling `notification-service` over
HTTP inside the orchestration. It was fire-and-forget, but it still coupled the
custody flow to a second service being reachable, and adding a second consumer
(email, chat) meant more calls in the orchestrator.

## Decision

- `assignment-service` publishes a `NotificationEvent(clientId, type, message)` to
  a **topic exchange** `asset-tracker.events` with routing key
  `assignment.<type>` (e.g. `assignment.asset-checked-out`). Publishing failures
  are logged, never propagated — the custody change has already committed.
- `notification-service` binds a durable queue `notification.events` to
  `assignment.#` and records each event with a `@RabbitListener`. The HTTP
  `POST /notifications` endpoint stays for direct use and tests.
- JSON payloads (`Jackson2JsonMessageConverter`); the event record is duplicated
  in both services (a shared contract module is a later refactor).
- RabbitMQ is a compose service; both apps still start if the broker is down
  (`missing-queues-fatal: false`) and reconnect.

## Consequences

- The orchestrator no longer knows or cares who consumes custody events; a new
  consumer is a new queue binding, not a code change in `assignment-service`.
- Events survive a `notification-service` restart (durable queue), unlike the old
  fire-and-forget HTTP call.
- One more piece of infrastructure to run. Delivery is at-least-once; the
  in-memory notification store is idempotent enough for the demo, a real store
  would dedupe on a message id.
