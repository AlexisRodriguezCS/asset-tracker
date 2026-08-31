# api-gateway

The single public entry point for the [ar-ecommerce-platform](https://github.com/ar-ecommerce-platform).
Routes `/api/**` to the backing services, enforces JWT auth, and applies CORS for browser clients.

- **Port:** 8080 — this is the only port clients should call
- **Routing:** Spring Cloud Gateway (server webmvc), `lb://` targets resolved via Eureka
- **Auth:** OAuth2 resource server with a multi-issuer resolver — the local `auth-service`
  issuer always, plus Microsoft Entra ID when `ENTRA_ISSUER_URI` is set (blank by default, so
  the platform runs with no Azure dependency)
- **CORS:** origins from `CORS_ALLOWED_ORIGINS` (defaults cover `localhost:3000` / `:5173`)

## Routes

| Public path | Forwards to |
|---|---|
| `/api/auth/**` | `auth-service` `/auth/**` — **public** (login, register) |
| `/api/users/**` | `user-service` `/users/**` |
| `/api/products/**` | `product-service` `/products/**` |
| `/api/inventory/**` | `inventory-service` `/inventory/**` |
| `/api/orders/**` | `order-service` `/orders/**` |
| `/api/payments/**` | `payment-service` `/payments/**` |
| `/api/notifications/**` | `notification-service` `/notifications/**` |

Everything except `/api/auth/**` and `/actuator/**` requires `Authorization: Bearer <jwt>`.

> On a cold start the `lb://` routes only activate after the gateway's first Eureka registry
> fetch (~20-30s after the container is healthy); until then protected paths return 401.
> `infra/scripts/demo-flow.*` wait for this.

Each service serves its own Swagger UI on its own port (e.g. `http://localhost:8083/swagger-ui.html`).

## Run

Whole platform (recommended):

```bash
docker compose -f ../infra/compose/docker-compose.yml up -d --build
```

This service alone (needs discovery-server + the target services running):

```bash
./gradlew bootRun
```

## Build & quality

```bash
./gradlew build          # compile + test + spotless + checkstyle (cyclomatic complexity <= 10) + jacoco report
./gradlew spotlessApply
```

Quality config is vendored: `gradle/quality.gradle`, `config/checkstyle/`.

## Testing

- **Smoke** — `ApiGatewayApplicationTests` boots the full gateway context (routes + security + the multi-issuer resolver), Eureka disabled.
- **End-to-end** — every request in the [e2e-tests](https://github.com/ar-ecommerce-platform/e2e-tests) suite goes through this gateway, so routing, `StripPrefix`, CORS and JWT enforcement are covered there.

## Config

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port |
| `JWT_SECRET` | dev fallback | HS256 secret shared with `auth-service` for local-token validation |
| `JWT_ISSUER` | `ecommerce-auth` | expected `iss` on local tokens |
| `ENTRA_ISSUER_URI` | _(blank)_ | set to `https://login.microsoftonline.com/<tenant>/v2.0` to also accept M365 tokens |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | browser origins |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | `http://localhost:8761/eureka/` | registry URL |

## Tech

Java 21 · Spring Boot 3.5.7 · Spring Cloud 2025.0.0 (`gateway-server-webmvc`, `loadbalancer`,
`netflix-eureka-client`, `config`) · Spring Security OAuth2 Resource Server · Gradle

See [infra/RUNBOOK.md](../infra/RUNBOOK.md) for the full platform runbook and the
"Sign in with Microsoft 365" setup.
