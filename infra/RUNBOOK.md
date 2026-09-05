# Runbook

Local, offline operation of the **asset-tracker** stack.

## Services & ports

| Service | Port | Notes |
|---|---|---|
| discovery-server | 8761 | Eureka registry; UI at http://localhost:8761 |
| config-server | 8888 | Spring Cloud Config, **native** backend over the mounted `config-repo` |
| api-gateway | **8080** | the only entry point; routes `/api/**`, public `GET`, JWT on writes, CORS, forwards `X-User-*` headers |
| auth-service | 8081 | register / login; issues RS256 JWT with `role` + `clientIds`; validates Entra id-tokens |
| assignment-service | 8082 | orchestrator: check-out / check-in / transfer / offboard; owns the assignment history |
| asset-service | 8083 | the asset records; guarded custody transitions |
| location-service | 8084 | sites / rooms / desks + QR tags |
| client-service | 8085 | tenants |
| notification-service | 8086 | in-memory event log (RabbitMQ consumer) |
| people-service | 8087 | employees, offboarding status |
| rabbitmq | 5672 / 15672 | broker + management UI at http://localhost:15672 (guest/guest) |
| prometheus | 9090 | scrapes every `/actuator/prometheus` |
| grafana | 3001 | http://localhost:3001 — anonymous viewer; the **asset-tracker** dashboard is pre-loaded |

Default: the JPA services run on in-memory **H2** — fast, **data resets on
every restart**. The PostgreSQL overlay makes it persist.

## Run it (Docker — supported path)

```bash
cd infra/compose
cp .env.example .env                  # defaults work for local; no JWT secret needed
docker compose -f docker-compose.yml up -d --build
docker compose ps                     # wait for all healthy (includes rabbitmq)
pwsh ../scripts/demo-flow.ps1          # or: bash ../scripts/demo-flow.sh
docker compose down
```

**Auth is RS256.** `auth-service` generates an RSA key pair at startup and serves
the public half at `http://localhost:8081/.well-known/jwks.json`; the gateway
reads it from `JWT_JWKS_URI`. Restarting `auth-service` rotates the key, so tokens
issued before the restart stop validating.

**Messaging.** `assignment-service` publishes custody events to the RabbitMQ
`asset-tracker.events` exchange; `notification-service` consumes them off a
durable queue. Management UI at `http://localhost:15672` (guest / guest). A
non-Docker run needs a local RabbitMQ or it just logs connection retries (the
publish is fire-and-forget, so the flow still works).

First build pulls base images + all Gradle dependencies; expect several minutes.
Build context is the **repo root** (monorepo) — each service builds its own
`bootJar` out of the Gradle multi-project.

## Run it against PostgreSQL (`prod` profile)

```bash
docker compose -f docker-compose.yml -f docker-compose.postgres.yml up -d --build
```

Starts one `postgres:16` container (a database per JPA service), sets
`SPRING_PROFILES_ACTIVE=prod`, and each service runs **Flyway** migrations then
`ddl-auto: validate`.

- Data survives `docker compose ... restart <service>`.
- `docker compose ... down -v` drops the `pgdata` volume (full reset).
- Inspect: `docker exec -it postgres psql -U att -d assetdb` -> `\dt`, `select * from assets;`
- Migrations live at `services/<svc>/src/main/resources/db/migration/V*.sql`.

## The demo flow

`infra/scripts/demo-flow.{ps1,sh}` drives everything through `http://localhost:8080`:

1. `GET /api/clients` -> 3 tenants
2. `GET /api/assets?clientId=1&status=IN_STOCK` -> pick an asset (public)
3. `GET /api/assets?clientId=1&type=LAPTOP` -> "all laptops"
4. `GET /api/people?clientId=1` -> pick a person (public)
5. `POST /api/assignments` with **no token** -> **401**
6. `POST /api/auth/login` (`tech@acme.example` / `Passw0rd!`) -> capture token
7. `POST /api/assignments` -> `open: true`, `checkedOutBy` = the tech's email
8. `GET /api/assets?...&holderType=PERSON&holderId=…` -> the asset shows on the person
9. `POST /api/assignments` again -> **409** (already assigned)
10. `POST /api/assignments/offboard?personId=…` -> `{returned:[...], failed:[]}`
11. `GET /api/assets/{id}` -> back to `IN_STOCK`
12. `GET /api/notifications?clientId=1` -> `ASSET_CHECKED_OUT`, `OFFBOARDING_COLLECTED`

`infra/http/platform.http` runs the same walkthrough from IntelliJ's HTTP client.

### The same flow as a test

`e2e/` is the REST-Assured version, driven through the gateway:

```bash
./gradlew :e2e:test                        # against a stack you already have up
E2E_BASE_URL=http://localhost:8080 E2E_REQUIRED=true ./gradlew :e2e:test
```

With no stack reachable the suite **skips** so `./gradlew build` stays green on a
bare checkout. `E2E_REQUIRED=true` turns that skip into a failure — CI's `e2e` job
sets it, because a suite that quietly skips itself in the one place it is meant to
run is worse than no suite. That job pulls the ten images the `images` job just
pushed (`docker-compose.ci.yml` pins them by commit SHA), waits for the gateway to
actually route rather than merely report healthy, then runs the suite.

## Seed data (dev profile)

- **clients:** Acme (1), Globex (2), Initech (3)
- **people (Acme):** Dana Reyes, Sam Okafor, Priya Nair, Leo Martins
- **locations (Acme):** HQ site, a Stockroom, `Desk 001`..`Desk 012` (`ACME-D-0nn`)
- **assets (Acme):** ~59 — 10 MacBook/Latitude laptops, iPads, Dell monitors,
  CalDigit docks, chargers, cables; all start in the stockroom
- **users:** `tech@acme.example` (TECH, clients 1-3), `hr@acme.example` (HR,
  client 1), `admin@platform.example` (ADMIN) — all password `Passw0rd!`

## API docs (Swagger)

Each REST service serves its own Swagger UI at
`http://localhost:<port>/swagger-ui.html` — e.g. asset-service at
http://localhost:8083/swagger-ui.html.

## Tracing a request

Every service tags each request with a correlation id in the log MDC. The gateway
mints one (or keeps a client-supplied `X-Correlation-Id`) and forwards it, and it
rides both outbound HTTP calls and the RabbitMQ hop. To follow one action:

```bash
CID=trace-$(date +%s)
curl -s -X POST localhost:8080/api/assignments -H "Authorization: Bearer $TOKEN" \
  -H "X-Correlation-Id: $CID" \
  -d '{"clientId":1,"assetId":54,"holderType":"PERSON","holderId":1}'
docker compose -f infra/compose/docker-compose.yml logs | grep "$CID"
```

Set `LOG_FORMAT=ecs` in `infra/compose/.env` for newline-delimited JSON logs
(Spring Boot native — no dependency). Distributed tracing to Zipkin is a
follow-up; the correlation id and MDC wiring are the groundwork.

## Rate limiting

The gateway allows **10 `POST /api/auth/**` per minute per client IP**; the 11th
gets `429` + `Retry-After: 60`. Behind a proxy the key is the first
`X-Forwarded-For` hop, so every caller isn't collapsed onto the ingress IP.

The budget is a property — `AUTH_RATE_LIMIT_MAX` / `AUTH_RATE_LIMIT_WINDOW_MS` —
because **counters live in each gateway's heap, not in a shared store**. N replicas
therefore allow roughly N × the budget, and a rolling restart resets every counter.
The cloud overlay runs two gateways and sets `AUTH_RATE_LIMIT_MAX: "5"` to keep the
cluster-wide rate near 10/min. That makes credential stuffing expensive; it is not a
hard limit. The real fix is Redis behind Spring Cloud Gateway's `RequestRateLimiter`.

## Startup convergence

A container reporting healthy does **not** mean it can call its neighbours. Reads
through the gateway come up first; a write like check-out additionally needs
`assignment-service` to have discovered `asset-service` through Eureka, and until
it has, the call returns `503 ASSET_SERVICE_UNAVAILABLE`.

Stock Eureka timings make that window long — 30s registry fetch, 30s heartbeat,
90s lease, plus the server's own 30s response cache. Measured on a cold
`compose up`: gateway routing at **53s**, first successful check-out at **73s**.

`config-repo/application.yml` now sets `registry-fetch-interval-seconds: 5` and a
10s heartbeat / 30s lease, and `discovery-server` sets
`response-cache-update-interval-ms: 5000`. Same measurement after: routing at
**29s**, first check-out at **38s** — a 9s gap instead of 20s. Ten small services
on one network can afford the extra gossip; the defaults are tuned for far larger
fleets where registry chatter actually costs something.

The gap is smaller, not gone. Anything scripted against a freshly-started stack
should wait for the write path, not just a health check — `infra/scripts/demo-flow.*`
and CI's `e2e` job both do.

## Scaling: what can and cannot run multi-replica

The cloud overlay (`deploy/k8s/overlays/cloud`) runs two of everything **except
`auth-service`**, which is pinned to one pod on purpose:

`JwtService` generates its RSA key pair in the constructor, so every pod signs with
a different key and publishes a different JWK set. The gateway resolves
`/.well-known/jwks.json` through the `auth-service` Service, so with two pods it
caches one pod's key and intermittently rejects the other pod's tokens. Scaling
auth-service out requires one shared signing key — a key pair mounted from a Secret,
or signing through Key Vault / KMS so the private key never leaves the HSM.

## Security & secrets

- **No secret is committed.** `infra/compose/.env` is gitignored;
  `infra/compose/.env.example` is the committed contract.
- Values reach the apps only as environment variables; `application.yml` files
  contain `${VAR}` references with obviously-non-production dev fallbacks.
- The gateway validates the JWT and forwards `X-User-Id` / `X-User-Role` /
  `X-Client-Ids` downstream, overriding anything the client sent.
- `gitleaks` runs in CI.

## "Sign in with Microsoft 365" (optional)

The console has a landing page at `/welcome` with **Sign in with Microsoft 365**
and **Sign in with email**. The Microsoft button is hidden (and the OIDC routes
bounce back) until an Entra app is configured — the stack runs with zero Azure
dependency by default.

**Flow.** The browser never touches tokens: `/api/auth/microsoft/start` (Next.js
route) begins an OIDC Authorization Code + PKCE flow; `/api/auth/microsoft/callback`
swaps the code for an id-token (confidential client), posts it to auth-service
`POST /auth/microsoft`, which validates it against Entra's JWKS, provisions a
first-seen user (role `TECH`, `ENTRA_DEFAULT_CLIENT_IDS`), and returns a normal
local RS256 session token. The httpOnly `att_session` cookie holds that local
token, so nothing downstream changes. The gateway's multi-issuer resolver still
accepts raw Entra tokens for API clients.

**To enable** (needs your tenant, ~10 min):

1. Azure Portal → App registrations → New registration "asset-tracker console".
2. Authentication → add a **Web** redirect URI `http://localhost:3000/api/auth/microsoft/callback`.
3. Certificates & secrets → new client secret.
4. `web/.env.local`: `AZURE_AD_TENANT_ID`, `AZURE_AD_CLIENT_ID`, `AZURE_AD_CLIENT_SECRET`, `APP_URL`.
5. `infra/compose/.env`: `ENTRA_ISSUER_URI=https://login.microsoftonline.com/<tenant-id>/v2.0`, `ENTRA_CLIENT_ID=<client-id>` (same as `AZURE_AD_CLIENT_ID`).
6. Restart auth-service and the web dev server.

The `ENTRA_DEFAULT_CLIENT_IDS` grant is a stand-in for a real group → client
mapping — swap it for one when the tenant's groups are known.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `port is already allocated` | another process on 8080-8888/8761; stop it or change the host port mapping |
| Gradle can't find JDK 21 | `export JAVA_HOME=~/.jdks/corretto-21.0.7` (or let toolchain auto-download) |
| a service stays `unhealthy` | `docker compose logs <service>`; usually still starting - `start_period` is 60s |
| gateway 401 on a `GET` | it's not in the public-GET list (only assets/people/locations/assignments/clients/notifications are) |
| gateway 503 on `/api/...` | the target hasn't registered in Eureka yet - check http://localhost:8761 |
| `./gradlew: Permission denied` in a Docker build | the image `chmod +x gradlew`s defensively; also `git update-index --chmod=+x gradlew` |
| rebuild one service | `docker compose up -d --build <service>` |

## Deferred — the next lessons

- Per-service tenant enforcement from the forwarded `X-Client-Ids`; verify a
  signed principal inside the mesh instead of trusting the gateway's headers.
- Persist / KMS-back the token-signing key so tokens survive an `auth-service`
  restart **and so auth-service can run more than one replica** (see "Scaling" above).
- A shared-store rate limiter (Redis) so the auth brake is cluster-wide rather than
  per gateway instance.
- Saga-style compensation when an offboarding sweep half-fails.
- Connection-pool tuning; one Postgres role per service; a managed instance in cloud.
- Contract tests (Spring Cloud Contract / Pact), mutation testing (PITest), load tests (k6).
- Observability: ELK / Prometheus + Grafana, distributed tracing.
- The **desk map** and the **mobile QR app**.
- Cloud: Kubernetes manifests exist (`deploy/k8s/`, Kustomize base + local/cloud
  overlays; see `docs/deployment.md`). Left to do: Terraform/Bicep for the cluster +
  managed Postgres/broker/Key Vault, External Secrets Operator, HPA/PDB, a live
  cluster. OIDC federation from GitHub Actions is stubbed in `.github/workflows/deploy.yml`.
