# asset-tracker

A multi-tenant **IT asset-tracking platform** — track which person or desk holds
which laptop / tablet / charger / cable, slice across an organisation ("all
laptops", "what's on desk 14", "everything in for repair"), and collect it all
back at offboarding.

Built as a Spring Cloud microservices backend + a Next.js console, in one
Gradle-multi-project monorepo, to demonstrate distributed-systems and DevOps
practice: service discovery, an API gateway with JWT, a synchronous orchestrator
with real failure paths, per-tenant scoping, containerised delivery, and CI that
publishes images.

---

## What it does

| Area | |
|---|---|
| **Assets** | one filterable list — by type, by status, by holder. Guarded status transitions. |
| **People** | employees of a client; `ACTIVE → OFFBOARDING → DEPARTED`. A desk is optional. |
| **Desks / rooms / sites** | each carries a QR tag; "what's on this desk" is a query, not stored state. |
| **Assignments** | check-out / check-in / transfer, and **offboard-all** for a departing employee. Append-only history — a return stamps `returnedAt`, it never deletes. |
| **Tenancy** | every record belongs to a `clientId`; the JWT carries the set a user may act on. |

The failure paths that make the orchestration interesting: checking out an asset
that is already assigned → **409**; one that is retired or lost → **422**;
returning one that was never checked out → **409**.

## Architecture

```
                         ┌──────────────┐
  browser ──HTTPS──▶      │  api-gateway │  routing · CORS · JWT · forwards
                          │    :8080     │  X-User-Id / X-User-Role / X-Client-Ids
                          └──────┬───────┘
        ┌───────────┬───────────┼───────────┬────────────┬─────────────┐
        ▼           ▼           ▼           ▼            ▼             ▼
   auth-service  client-    people-    location-    asset-       assignment-
     :8081      service     service     service     service        service
                 :8085      :8087       :8084       :8083          :8082
                                                       ▲   ┌──────────┘
                                                       └───┤ calls asset-service
                                                           │ (assign / return / search)
                                                     notification-service :8086
   discovery-server :8761 (Eureka)   ·   config-server :8888 (Spring Cloud Config, native)
```

`assignment-service` is the orchestrator. `POST /assignments` is **not**
`@Transactional` — it spans HTTP calls — so persistence is split into the short
independent transactions of `AssignmentTransactions`, and a failed downstream
call still leaves a correct history row.

Full diagrams and the decision records are in [`docs/`](docs/).

## Repository layout

```
services/            10 Spring Boot services (Gradle sub-projects)
web/                  Next.js 15 console (Assets / People / Desks)
infra/compose/        docker-compose + the Postgres/Flyway overlay
config-repo/          Spring Cloud Config native backend
e2e/                  REST-Assured suite through the gateway
docs/                 architecture + ADRs
build.gradle          root: shared Java 21 / Spring Cloud BOM / quality gates
```

One `gradlew`, one `.github/workflows/ci.yml`.

## Run it

```bash
cd infra/compose
printf 'JWT_SECRET=%s\nJWT_ISSUER=asset-tracker-auth\n' "$(openssl rand -hex 32)" > .env
docker compose -f docker-compose.yml up -d --build      # 10 containers, H2 in-memory
docker compose ps                                        # wait for healthy
```

Then the console:

```bash
cd web && cp .env.example .env.local && npm install && npm run dev   # localhost:3000
```

Seeded login: `tech@acme.example` / `Passw0rd!` (also `hr@…`, `admin@…`).
Seed data: 3 clients, ~59 assets for Acme (10 laptops), 4 people, 12 desks.

Persist data with the Postgres overlay:

```bash
docker compose -f docker-compose.yml -f docker-compose.postgres.yml up -d --build
```

Everything also runs from the repo root: `./gradlew build` (compile + test +
Spotless + Checkstyle + JaCoCo for all services), `./gradlew :asset-service:bootRun`, etc.

## The demo flow

Browse the catalog (public) → sign in as the tech → check an asset out to a
person → see it on that person → try to check it out again (**409**) → run
offboarding → the asset is back in stock, and `notification-service` has the
`ASSET_CHECKED_OUT` / `OFFBOARDING_COLLECTED` events. Scripted in
[`e2e/`](e2e/src/test/java/com/assettracker/e2e/PlatformE2ETest.java).

## Testing

| Layer | Where |
|---|---|
| Unit (service logic, guarded transitions) | each service `src/test` |
| Controller slice (`@WebMvcTest` + MockMvc) | the REST services |
| Repository slice (`@DataJpaTest`) | the JPA services with custom queries |
| End-to-end (REST-Assured through the gateway) | `e2e/`, self-skips with no stack |

## CI

`.github/workflows/ci.yml`: one Gradle build, the web build (lint + typecheck +
`next build`), a gitleaks scan, and — on `main` — a matrix that builds and pushes
all ten service images to `ghcr.io/<owner>/asset-tracker-<service>`.

## Not done yet

Visual floor map of desks; a mobile app that scans a desk/asset QR; RS256 + JWKS;
Resilience4j around the orchestrator's calls; event-driven notifications;
cloud hosting.
