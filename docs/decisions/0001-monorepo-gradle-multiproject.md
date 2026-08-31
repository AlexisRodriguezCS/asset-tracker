# 0001 — One monorepo, one Gradle multi-project build

**Status:** accepted

## Context

The predecessor project ran as ~16 separate repos under a GitHub org: per-repo
CI, per-repo branch protection, a duplicated `gradle/quality.gradle` in each, and
a Dependabot PR flood on first push. It proved the polyrepo pattern but the
overhead was real.

## Decision

Everything lives in one repository:

```
services/<10>/   web/   infra/   config-repo/   e2e/   docs/
```

A root `settings.gradle` includes each service as a flat Gradle module whose
`projectDir` points into `services/`. A root `build.gradle` applies Java 21, the
Spring Cloud BOM and the quality gates to every subproject. One `gradlew`, one
`gradle/quality.gradle`, one `config/checkstyle/`.

## Consequences

- `./gradlew build` builds and checks the whole backend; a reviewer clones once
  and runs one `docker compose up`.
- One CI workflow: a single Gradle build, the web build, one gitleaks scan, and a
  matrix that publishes the images.
- Per-service `build.gradle` files shrink to ~15 lines (their own deps + jar name).
- Trade-off: no independent versioning or per-service access control. Acceptable
  for a single-owner platform; the polyrepo skill is demonstrated elsewhere.
