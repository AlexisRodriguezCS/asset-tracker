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
| **Assets** | one filterable list — by type, status, holder, or free-text (tag / serial / model / holder). Each asset carries a condition, deploy + warranty dates, and a lifecycle status (`IN_STOCK · ASSIGNED · IN_REPAIR · BROKEN · PENDING_RECYCLE · RECYCLED · RETIRED · LOST`) with guarded transitions. Add / edit inline; **retire-and-replace** marks a unit gone and opens a pre-filled form for its replacement. |
| **Asset types** | a per-client list the techs curate — add a type (rejected if the name is already taken), remove one (blocked while assets use it, or move them to another type first). |
| **People** | employees of a client; `ACTIVE → OFFBOARDING → DEPARTED`. A desk is optional. |
| **Desks / rooms / sites** | each carries a QR tag and a building + floor; the console lays desks out as a building → floor map. "What's on this desk" is a query, not stored state. |
| **Assignments** | check-out / check-in / transfer, and **offboard-all** for a departing employee. Append-only history — a return stamps `returnedAt`, it never deletes. |
| **Reports** | live rollups per client: by type / status / condition / department, lifecycle events from the audit trail, break-and-loss by department, most-replaced tag slots, fleet value. |
| **Audit trail** | every mutating call writes a `who / what / when` row in the same transaction as the change. |
| **Tenancy** | every record belongs to a `clientId`; the JWT carries the set a user may act on. |

**A tag identifies a slot, not a unit** ([ADR 0007](docs/decisions/0007-tags-identify-slots-not-units.md)):
uniqueness is per `(client, tag, type)` and only among in-service statuses, so a
laptop and its bundled charger + cable can share one tag, and a lost accessory is
replaced on the same tag while the dead row stays for history.

The failure paths that make the orchestration interesting: checking out an asset
that is already assigned → **409**; one that is retired or lost → **422**;
returning one that was never checked out → **409**.

## Architecture

```
                         ┌──────────────┐
  browser ──HTTPS──▶      │  api-gateway │  routing · CORS · RS256 JWT (JWKS) · forwards
                          │    :8080     │  X-User-Id / X-User-Role / X-Client-Ids
                          └──────┬───────┘
        ┌───────────┬───────────┼───────────┬────────────┬─────────────┐
        ▼           ▼           ▼           ▼            ▼             ▼
   auth-service  client-    people-    location-    asset-       assignment-
     :8081      service     service     service     service        service
   signs RS256   :8085      :8087       :8084       :8083          :8082
   /jwks.json                                          ▲   ┌──────────┘
                                                       └───┤ assign / return / search
                                                           │ (Resilience4j: retry + breaker)
                              RabbitMQ ◀── publishes custody events ──┘
                                 │
                                 ▼
                       notification-service :8086  (consumes the events)

   discovery-server :8761 (Eureka)   ·   config-server :8888 (Spring Cloud Config, native)
```

`assignment-service` is the orchestrator. `POST /assignments` is **not**
`@Transactional` — it spans HTTP calls — so persistence is split into the short
independent transactions of `AssignmentTransactions`, and a failed downstream
call still leaves a correct history row. Its call to `asset-service` is wrapped in
a Resilience4j retry + circuit breaker; custody events go to `notification-service`
over RabbitMQ, not a synchronous call.

Full diagrams and the decision records are in [`docs/`](docs/).

## Repository layout

```
services/            10 Spring Boot services (Gradle sub-projects)
web/                  Next.js 15 console (Assets / People / Desks / Types / Reports)
infra/compose/        docker-compose + the Postgres/Flyway overlay
deploy/k8s/           Kustomize base + local/cloud overlays (see docs/deployment.md)
config-repo/          Spring Cloud Config native backend
e2e/                  REST-Assured suite through the gateway
docs/                 architecture + ADRs
build.gradle          root: shared Java 21 / Spring Cloud BOM / quality gates
```

One `gradlew`, one `.github/workflows/ci.yml`.

## Run it

```bash
cd infra/compose
cp .env.example .env                                    # defaults are fine for local
docker compose -f docker-compose.yml up -d --build      # 11 containers (+ RabbitMQ), H2 in-memory
docker compose ps                                        # wait for healthy
```

No `JWT_SECRET` — `auth-service` generates an RS256 key pair at startup and the
gateway validates tokens against its `/.well-known/jwks.json`.

Then the console:

```bash
cd web && cp .env.example .env.local && npm install && npm run dev   # localhost:3000
```

Seeded login: `tech@acme.example` / `Passw0rd!` (also `hr@…`, `admin@…`).
Seed data: 3 clients (Acme / Globex / Initech), each with its own type list,
people, desks across two buildings and several floors, person + desk kits, and a
spread of statuses — ~145 assets in total.

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

Per-service tenant enforcement from the forwarded `X-Client-Ids`; a persisted /
KMS-backed token-signing key; saga compensation for a half-finished offboarding
sweep; a visual floor-plan map of desks; a mobile app that scans a desk/asset QR.
Cloud hosting has Kubernetes manifests (`deploy/k8s/`) that validate offline but
no live cluster or infra-as-code yet — see [docs/deployment.md](docs/deployment.md).
