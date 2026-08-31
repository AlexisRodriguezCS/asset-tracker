# docs

Architecture and design notes for **asset-tracker** — a multi-tenant IT
asset-tracking platform (Spring Cloud microservices + a Next.js console) built to
practise distributed-systems design and DevOps.

- **[architecture.md](architecture.md)** — context / container / component views,
  the check-out and offboarding sequences, cross-cutting concerns, what's deferred.
- **[decisions/](decisions/)** — Architecture Decision Records: the *why* behind
  the choices that would otherwise look arbitrary.

## The system in one paragraph

A browser (later, a mobile QR scanner) calls a single **API gateway** (`:8080`),
which validates a JWT and routes `/api/**` to one of seven business services via
**Eureka** discovery + client-side load balancing. Reads are public; writes need
a token. **auth-service** issues local JWTs carrying the user's `role` and the
`clientIds` (tenants) they may act on; the gateway forwards that identity
downstream as headers. **asset-service** owns each asset's live custody state
with guarded transitions; **assignment-service** is the orchestrator — checking
an asset out fans out to asset-service and notification-service over HTTP and
keeps an append-only assignment history. Each service owns its database.
**config-server** serves shared configuration from a native backend. Everything
runs with one `docker compose up`; a Postgres + Flyway overlay makes data persist.
