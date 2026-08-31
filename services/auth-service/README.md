# auth-service

Authentication for the [ar-ecommerce-platform](https://github.com/ar-ecommerce-platform):
registration, login, and JWT issuance/validation.

- **Port:** 8081
- **Tokens:** HS256 JWT with `iss` + `roles` claims, shared secret with the gateway
- **Persistence:** `users` table. Dev: in-memory H2 (resets on restart). `prod`: PostgreSQL + Flyway.
- **Registers with:** Eureka (discovery-server :8761)

## Endpoints

Reached through the gateway as `/api/auth/**`.

| Method | Path | Body | Result |
|---|---|---|---|
| `POST` | `/auth/register` | `{ email, password }` | `201` |
| `POST` | `/auth/login` | `{ email, password }` | `200 { token, tokenType, expiresInMs }` |
| `GET` | `/auth/validate` | header `Authorization: Bearer <t>` | `200 { subject, roles }` or `401` |

Errors: `409` email already registered, `401` bad credentials / invalid token, `400` validation.

**API docs:** Swagger UI at `http://localhost:8081/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`).

## Run

Whole platform (recommended):

```bash
docker compose -f ../infra/compose/docker-compose.yml up -d --build
```

This service alone:

```bash
./gradlew bootRun
# or
docker build -t ecom/auth-service . && docker run --rm -p 8081:8081 ecom/auth-service
```

## Build & quality

```bash
./gradlew build          # compile + test + spotless + checkstyle (cyclomatic complexity <= 10) + jacoco report
./gradlew spotlessApply
```

Quality config is vendored: `gradle/quality.gradle`, `config/checkstyle/`.

## Testing

`./gradlew test` runs every layer below; `./gradlew build` also runs Checkstyle + Spotless and writes a JaCoCo report to `build/reports/jacoco/`.

- **Smoke** — `AuthServiceApplicationTests`: the full Spring context starts.
- **Unit** — `service/AuthServiceTest` (JUnit 5 + Mockito + AssertJ): duplicate-email registration is rejected; login returns a Bearer token; a wrong password throws `BadCredentialsException`.
- **API / web slice** — `controller/AuthControllerTest` (`@WebMvcTest` + MockMvc, service mocked): `POST /auth/register` → 201 / 409; `POST /auth/login` → token JSON; bad credentials → 401 with the `ApiError` body; `GET /auth/validate` echoes the token subject.
- **Repository slice** — `repository/UserRepositoryTest` (`@DataJpaTest`): `findByEmail` / `existsByEmail` against an embedded database.

End-to-end auth (register → login → bearer) is covered through the gateway in [e2e-tests](https://github.com/ar-ecommerce-platform/e2e-tests).

## Config

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8081` | HTTP port |
| `JWT_SECRET` | dev fallback (>= 32 bytes) | HS256 signing secret — inject the real value from the environment |
| `JWT_EXPIRATION_MS` | `3600000` | token lifetime |
| `JWT_ISSUER` | `ecommerce-auth` | `iss` claim |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | `http://localhost:8761/eureka/` | registry URL |
| `SPRING_PROFILES_ACTIVE` | _(none)_ | set to `prod` to use PostgreSQL + Flyway instead of H2 |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | - | Postgres connection (`prod` only) |

## Tech

Java 21 · Spring Boot 3.5.7 · Spring Security · jjwt 0.13 · Spring Data JPA (H2 / PostgreSQL + Flyway) ·
Spring Cloud 2025.0.0 (`netflix-eureka-client`) · Gradle
