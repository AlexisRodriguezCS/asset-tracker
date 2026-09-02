# Runbook

Local, offline operation of the **asset-tracker** stack.

## Services & ports

| Service | Port | Notes |
|---|---|---|
| discovery-server | 8761 | Eureka registry; UI at http://localhost:8761 |
| config-server | 8888 | Spring Cloud Config, **native** backend over the mounted `config-repo` |
| api-gateway | **8080** | the only entry point; routes `/api/**`, public `GET`, JWT on writes, CORS, forwards `X-User-*` headers |
| auth-service | 8081 | register / login; issues HS256 JWT with `role` + `clientIds` |
| assignment-service | 8082 | orchestrator: check-out / check-in / transfer / offboard; owns the assignment history |
| asset-service | 8083 | the asset records; guarded custody transitions |
| location-service | 8084 | sites / rooms / desks + QR tags |
| client-service | 8085 | tenants |
| notification-service | 8086 | in-memory event log |
| people-service | 8087 | employees, offboarding status |

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
- Persist / KMS-back the token-signing key so tokens survive an `auth-service` restart.
- Saga-style compensation when an offboarding sweep half-fails.
- Connection-pool tuning; one Postgres role per service; a managed instance in cloud.
- Contract tests (Spring Cloud Contract / Pact), mutation testing (PITest), load tests (k6).
- Observability: ELK / Prometheus + Grafana, distributed tracing.
- The **desk map** and the **mobile QR app**.
- Cloud: Kubernetes manifests exist (`deploy/k8s/`, Kustomize base + local/cloud
  overlays; see `docs/deployment.md`). Left to do: Terraform/Bicep for the cluster +
  managed Postgres/broker/Key Vault, External Secrets Operator, HPA/PDB, a live
  cluster. OIDC federation from GitHub Actions is stubbed in `.github/workflows/deploy.yml`.
