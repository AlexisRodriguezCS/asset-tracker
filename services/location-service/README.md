# location-service

Stock levels and reservations for the [ar-ecommerce-platform](https://github.com/ar-ecommerce-platform).

- **Port:** 8084
- **Persistence:** `inventory_items`, keyed by `productId`. Seeded for ids 1..5; **product 5 low (qty 3)** for the out-of-stock demo. Dev: in-memory H2. `prod`: PostgreSQL + Flyway.
- **Registers with:** Eureka (discovery-server :8761)
- Optimistic locking via a JPA `@Version` column.

## Endpoints

Reached through the gateway as `/api/inventory/**`.

| Method | Path | Body | Result |
|---|---|---|---|
| `GET` | `/inventory/{productId}` | — | `{ productId, quantityAvailable }`, `404` if unknown |
| `POST` | `/inventory/{productId}/reserve` | `{ quantity }` | `200` decremented, `409 INSUFFICIENT_STOCK`, `404` unknown |

**API docs:** Swagger UI at `http://localhost:8084/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`).

## Run

Whole platform (recommended):

```bash
docker compose -f ../infra/compose/docker-compose.yml up -d --build
```

This service alone:

```bash
./gradlew bootRun
# or
docker build -t ecom/location-service . && docker run --rm -p 8084:8084 ecom/location-service
```

## Build & quality

```bash
./gradlew build          # compile + test + spotless + checkstyle (cyclomatic complexity <= 10) + jacoco report
./gradlew spotlessApply
```

Quality config is vendored: `gradle/quality.gradle`, `config/checkstyle/`.

## Testing

`./gradlew test` runs every layer below; `./gradlew build` also runs Checkstyle + Spotless and writes a JaCoCo report.

- **Smoke** — `InventoryServiceApplicationTests`: the full Spring context starts.
- **Unit** — `entity/InventoryItemTest`: `reserve` decrements available stock and throws `InsufficientStockException` when asked for more than is available, leaving stock unchanged.
- **API / web slice** — `web/InventoryControllerTest` (`@WebMvcTest`): `GET /inventory/{id}`; `POST /inventory/{id}/reserve` → 200 with remaining stock, → 409 `INSUFFICIENT_STOCK`, → 404 for an unknown product.

## Config

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8084` | HTTP port |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | `http://localhost:8761/eureka/` | registry URL |
| `SPRING_PROFILES_ACTIVE` | _(none)_ | set to `prod` to use PostgreSQL + Flyway instead of H2 |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | - | Postgres connection (`prod` only) |

## Tech

Java 21 · Spring Boot 3.5.7 · Spring Data JPA (H2 / PostgreSQL + Flyway) · Bean Validation ·
Spring Cloud 2025.0.0 (`netflix-eureka-client`) · Gradle

See [infra/RUNBOOK.md](../infra/RUNBOOK.md) for the full platform runbook.
