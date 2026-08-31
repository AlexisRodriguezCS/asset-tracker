# notification-service

Notifications for the [ar-ecommerce-platform](https://github.com/ar-ecommerce-platform).

> **Demo stand-in.** Notifications are held in memory and logged — no real email / SMS delivery.

- **Port:** 8086
- **Persistence:** in-memory list — resets on restart
- **Registers with:** Eureka (discovery-server :8761)

## Endpoints

Reached through the gateway as `/api/notifications/**`.

| Method | Path | Body / query | Result |
|---|---|---|---|
| `POST` | `/notifications` | `{ userId, type, message }` | `201` — stored and logged |
| `GET` | `/notifications` | `?userId=` (optional) | that user's notifications, or all |

`order-service` calls `POST /notifications` (fire-and-forget) when an order is confirmed.

**API docs:** Swagger UI at `http://localhost:8086/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`).

## Run

Whole platform (recommended):

```bash
docker compose -f ../infra/compose/docker-compose.yml up -d --build
```

This service alone:

```bash
./gradlew bootRun
# or
docker build -t ecom/notification-service . && docker run --rm -p 8086:8086 ecom/notification-service
```

## Build & quality

```bash
./gradlew build          # compile + test + spotless + checkstyle (cyclomatic complexity <= 10) + jacoco report
./gradlew spotlessApply
```

Quality config is vendored: `gradle/quality.gradle`, `config/checkstyle/`.

## Testing

`./gradlew test` runs every layer below; `./gradlew build` also runs Checkstyle + Spotless and writes a JaCoCo report.

- **Smoke** — `NotificationServiceApplicationTests`: the full Spring context starts.
- **Unit** — `service/NotificationServiceTest`: `forUser` returns only that user's notifications.
- **API / web slice** — `web/NotificationControllerTest` (`@WebMvcTest`): `POST /notifications` → 201; `GET /notifications?userId=` filters; blank fields → 400.

## Config

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8086` | HTTP port |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | `http://localhost:8761/eureka/` | registry URL |

## Tech

Java 21 · Spring Boot 3.5.7 · Bean Validation ·
Spring Cloud 2025.0.0 (`netflix-eureka-client`) · Gradle

See [infra/RUNBOOK.md](../infra/RUNBOOK.md) for the full platform runbook.
