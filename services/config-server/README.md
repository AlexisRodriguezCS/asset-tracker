# config-server

Spring Cloud Config Server for the [ar-ecommerce-platform](https://github.com/ar-ecommerce-platform).
Serves per-service configuration from the [`config-repo`](https://github.com/ar-ecommerce-platform/config-repo).

- **Port:** 8888
- **Backend:** `native` — reads a local directory (the sibling `config-repo` checkout locally,
  a mounted volume in Docker). Not the Git backend; `CONFIG_REPO_LOCATIONS` overrides the path.
- **Registers with:** Eureka (discovery-server :8761)
- Not load-bearing for the platform — every service also ships a self-contained `application.yml`
  and imports from here as `optional:`.

## Endpoints

Standard Spring Cloud Config Server API:

| Path | Returns |
|---|---|
| `GET /{application}/{profile}` | resolved properties for a service + profile |
| `GET /{application}-{profile}.yml` | same, as YAML |
| `GET /actuator/health` | health |

Example: `curl http://localhost:8888/auth-service/default`

## Run

Whole platform (recommended):

```bash
docker compose -f ../infra/compose/docker-compose.yml up -d --build
```

This service alone (resolves `../config-repo`):

```bash
./gradlew bootRun
# or
docker build -t ecom/config-server .
docker run --rm -p 8888:8888 -v "$(pwd)/../config-repo:/config-repo:ro" \
  -e CONFIG_REPO_LOCATIONS='file:/config-repo,file:/config-repo/{application}' ecom/config-server
```

## Build & quality

```bash
./gradlew build          # compile + test + spotless + checkstyle (cyclomatic complexity <= 10) + jacoco report
./gradlew spotlessApply
```

Quality config is vendored: `gradle/quality.gradle`, `config/checkstyle/`.

## Testing

- **Smoke** — `EcommerceConfigServerApplicationTests` boots the context against the `native` backend using a classpath config fixture, with Eureka disabled.

## Config

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8888` | HTTP port |
| `CONFIG_REPO_LOCATIONS` | `file:../config-repo,file:../config-repo/{application}` | native search locations |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | `http://localhost:8761/eureka/` | registry URL |

## Tech

Java 21 · Spring Boot 3.5.7 · Spring Cloud 2025.0.0 (`config-server`, `netflix-eureka-client`) · Gradle

See [infra/RUNBOOK.md](../infra/RUNBOOK.md) for the full platform runbook.
