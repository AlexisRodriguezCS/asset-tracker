# discovery-server

Netflix Eureka service registry for the [ar-ecommerce-platform](https://github.com/ar-ecommerce-platform).
Every other service registers here; the gateway and `order-service` use it to resolve `lb://` addresses.

- **Port:** 8761 — dashboard at http://localhost:8761
- **Self-registration:** off (`register-with-eureka: false`, `fetch-registry: false`)
- **Persistence:** none

## Run

Whole platform (recommended):

```bash
docker compose -f ../infra/compose/docker-compose.yml up -d --build
```

This service alone:

```bash
./gradlew bootRun
# or
docker build -t ecom/discovery-server . && docker run --rm -p 8761:8761 ecom/discovery-server
```

## Build & quality

```bash
./gradlew build          # compile + test + spotless + checkstyle (cyclomatic complexity <= 10) + jacoco report
./gradlew spotlessApply  # auto-format (google-java-format)
```

Quality config is vendored: `gradle/quality.gradle`, `config/checkstyle/`.

## Testing

- **Smoke** — `DiscoveryServerApplicationTests` boots the full Eureka-server context and runs `main(...)`.

## Config

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8761` | HTTP port |

## Tech

Java 21 · Spring Boot 3.5.7 · Spring Cloud 2025.0.0 (`netflix-eureka-server`) · Gradle

See [infra/RUNBOOK.md](../infra/RUNBOOK.md) for the full platform runbook.
