# client-service

User profile management for the [ar-ecommerce-platform](https://github.com/ar-ecommerce-platform).

- **Port:** 8085
- **Persistence:** `user_profiles` table. Dev: in-memory H2 (resets on restart). `prod`: PostgreSQL + Flyway.
- **Registers with:** Eureka (discovery-server :8761)

## Endpoints

Reached through the gateway as `/api/users/**`.

| Method | Path | Body / query | Result |
|---|---|---|---|
| `POST` | `/users` | `{ email, displayName }` | `201` — idempotent: returns the existing profile for a known email |
| `GET` | `/users` | — | list all |
| `GET` | `/users/{id}` | — | one profile, `404` if missing |
| `GET` | `/users/by-email` | `?email=` | one profile, `404` if missing |

**API docs:** Swagger UI at `http://localhost:8085/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`).

## Run

Whole platform (recommended):

```bash
docker compose -f ../infra/compose/docker-compose.yml up -d --build
```

This service alone:

```bash
./gradlew bootRun
# or
docker build -t ecom/client-service . && docker run --rm -p 8085:8085 ecom/client-service
```

## Build & quality

```bash
./gradlew build          # compile + test + spotless + checkstyle (cyclomatic complexity <= 10) + jacoco report
./gradlew spotlessApply
```

Quality config is vendored: `gradle/quality.gradle`, `config/checkstyle/`.

## Testing

`./gradlew test` runs every layer below; `./gradlew build` also runs Checkstyle + Spotless and writes a JaCoCo report.

- **Smoke** — `UserServiceApplicationTests`: the full Spring context starts.
- **Unit** — `service/UserProfileServiceTest`: `createOrGet` saves a new email and returns the existing profile for a known one; `getById` throws when missing.
- **API / web slice** — `web/UserControllerTest` (`@WebMvcTest`): `POST /users` → 201; an invalid email → 400 `VALIDATION_FAILED`; `GET /users/{id}` missing → 404 `USER_NOT_FOUND`.
- **Repository slice** — `repository/UserProfileRepositoryTest` (`@DataJpaTest`): `findByEmail` / `existsByEmail`.

## Config

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8085` | HTTP port |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | `http://localhost:8761/eureka/` | registry URL |
| `SPRING_PROFILES_ACTIVE` | _(none)_ | set to `prod` to use PostgreSQL + Flyway instead of H2 |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | - | Postgres connection (`prod` only) |

## Tech

Java 21 · Spring Boot 3.5.7 · Spring Data JPA (H2 / PostgreSQL + Flyway) · Bean Validation ·
Spring Cloud 2025.0.0 (`netflix-eureka-client`) · Gradle

See [infra/RUNBOOK.md](../infra/RUNBOOK.md) for the full platform runbook.
