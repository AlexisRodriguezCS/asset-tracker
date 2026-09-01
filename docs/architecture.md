# Architecture

## 1. Context

Who uses the system and what it depends on.

```mermaid
flowchart TB
    tech([IT tech])
    hr([HR - offboarding])
    scanner([Mobile QR scanner - planned])
    entra[[Microsoft Entra ID - optional]]

    subgraph platform ["asset-tracker"]
        api[API Gateway<br/>:8080]
    end

    tech --> console([Web console]) --> api
    hr --> console
    scanner -.-> api
    console -. sign in .-> entra
    api -. validate token .-> entra
```

- The only public entry point is the **API gateway**. Everything else is internal.
- Two kinds of user: **IT techs** (assign, transfer, repair, retire) and **HR**
  (run offboarding collection). Roles: `TECH`, `HR`, `ADMIN`.
- **Multi-tenant.** Each customer company is a *client*. Every asset, person and
  location belongs to one client; a JWT carries the set of client ids its holder
  may act on.
- Sign-in is a local account (JWT from `auth-service`) or **Microsoft Entra ID**
  when `ENTRA_ISSUER_URI` is set. The mobile QR scanner is a later milestone; the
  API is already shaped for it (`/api/**` JSON, bearer auth, `/locations/by-qr`,
  `/assets/by-tag`).

## 2. Containers

Each box is a separately deployable service with its own database.

```mermaid
flowchart TB
    client([Web console])

    client --> gw[api-gateway :8080<br/>routing · CORS · JWT · forwards X-User-* headers]

    gw --> auth[auth-service :8081]
    gw --> cl[client-service :8085]
    gw --> ppl[people-service :8087]
    gw --> loc[location-service :8084]
    gw --> ast[asset-service :8083]
    gw --> asg[assignment-service :8082]
    gw --> ntf[notification-service :8086]

    asg -->|assign / return / search| ast
    asg -->|record| ntf

    auth --- authdb[(auth db)]
    cl --- cldb[(client db)]
    ppl --- ppldb[(people db)]
    loc --- locdb[(location db)]
    ast --- astdb[(asset db)]
    asg --- asgdb[(assignment db)]

    subgraph platform_services ["platform"]
        disc[discovery-server :8761<br/>Eureka]
        cfg[config-server :8888<br/>Spring Cloud Config]
    end

    gw -. resolve lb:// .-> disc
    asg -. resolve lb:// .-> disc
    gw -. optional config .-> cfg
```

- **Discovery** — every service registers with Eureka; the gateway and
  `assignment-service` resolve `lb://<service>` through it with client-side load
  balancing.
- **Config** — `config-server` serves shared settings from `config-repo` (native
  backend). Not load-bearing: every service ships a self-contained
  `application.yml` and imports from config-server as `optional:`.
- **Databases** — in-memory H2 by default (data resets on restart); PostgreSQL
  under the `prod` profile, one logical database per JPA service, Flyway owns the
  schema. `notification-service` has no database (in-memory list).

## 3. Checking an asset out

`assignment-service` is the orchestrator. `POST /api/assignments` fans out over HTTP.

```mermaid
sequenceDiagram
    autonumber
    participant C as Console
    participant G as api-gateway
    participant A as assignment-service
    participant S as asset-service
    participant N as notification-service

    C->>G: POST /api/assignments (Bearer JWT)
    G->>G: validate token; add X-User-Id / X-Client-Ids
    G->>A: POST /assignments
    A->>S: POST /assets/{id}/assign {holderType, holderId}
    alt asset already ASSIGNED
        S-->>A: 409
        A-->>G: 409 ASSET_UNAVAILABLE
    else asset retired / lost / recycled
        S-->>A: 422
        A-->>G: 422 ASSET_NOT_MOVABLE
    else free to move
        S-->>A: 200 (status ASSIGNED, holder set)
        A->>A: open Assignment row (short tx)
        A->>N: POST /notifications (fire-and-forget)
        A-->>G: 201 assignment {open: true}
    end
    G-->>C: response
```

**Why the persistence is split.** The flow is *not* wrapped in one
`@Transactional` — that would hold a DB connection open across the HTTP calls and
roll back the history row on a thrown failure. `AssignmentTransactions` exposes
`open` / `close` / reads, each its own short transaction; the orchestrator method
is not transactional.

## 4. Offboarding

```mermaid
sequenceDiagram
    autonumber
    participant C as Console (HR)
    participant A as assignment-service
    participant S as asset-service
    participant N as notification-service

    C->>A: POST /api/assignments/offboard?personId=P
    A->>S: GET /assets?holderType=PERSON&holderId=P
    S-->>A: [asset ids]
    loop each asset (best effort)
        A->>S: POST /assets/{id}/return
        A->>A: close the open Assignment
    end
    A->>N: POST /notifications (X returned, Y outstanding)
    A-->>C: OffboardingResult {returned:[...], failed:[...]}
```

One stuck return does not abort the sweep — the result lists what came back and
what is still out for a human to chase.

## 5. Cross-cutting

| Concern | How | Status |
|---|---|---|
| **AuthN** | JWT bearer. `auth-service` issues HS256 tokens with `sub`, `role`, `clientIds`. Gateway is an OAuth2 resource server, multi-issuer (local always, Entra when configured). | working; RS256 + JWKS is a planned upgrade |
| **AuthZ / tenancy** | Gateway enforces "authenticated" on writes, public on `GET`. It forwards `X-User-Id` / `X-User-Role` / `X-Client-Ids` (spoof-proof). Per-tenant enforcement in each service from those headers is a follow-up; today lists take an explicit `clientId`. | partial |
| **Config** | `config-server` (native) + per-service `application.yml` + env vars. | working |
| **Persistence** | JPA. Dev: H2 + `ddl-auto: update`. `prod`: PostgreSQL, Flyway migrations, Hibernate `validate`. One DB per service. | working (local overlay) |
| **Resilience** | 2s connect / 3s read timeouts on `assignment-service` clients; notification call is fire-and-forget; offboarding is best-effort per asset. | timeouts only; retries + circuit breakers deferred |
| **Observability** | SLF4J + console logging; Actuator health/info. | structured logs + tracing deferred |
| **API docs** | springdoc OpenAPI per service (`/swagger-ui.html`). | working |
| **Quality** | Spotless (google-java-format) + Checkstyle (cyclomatic complexity ≤ 10) + JaCoCo, applied once from the root build. | build gates working |
| **CI/CD** | One GitHub Actions workflow: Gradle build + web build + gitleaks; on `main`, a matrix publishes 10 GHCR images. | working |
| **Testing** | Unit, `@WebMvcTest`, `@DataJpaTest`, REST-Assured e2e through the gateway. | working |

## 6. Deferred — the roadmap

1. Per-service tenant enforcement from the forwarded `X-Client-Ids`.
2. RS256 + JWKS; forward a verified principal instead of trusting headers within the mesh.
3. Resilience4j (retry, circuit breaker) + compensation when an offboarding sweep half-fails.
4. Structured logging + correlation id + metrics/tracing.
5. Event-driven notifications (a broker) replacing the synchronous call.
6. **Visual floor map** of desks; **mobile app** that scans a desk/asset QR → what's assigned.
7. Cloud hosting: managed PostgreSQL, container registry, secrets store, public HTTPS.
