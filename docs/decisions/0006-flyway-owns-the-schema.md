# 0006 — Flyway owns the schema under the `prod` profile

**Status:** accepted

## Context

Hibernate `ddl-auto: update` is convenient in development and dangerous in
anything that keeps its data.

## Decision

- **Dev (default profile):** H2 in-memory, `ddl-auto: update`, Flyway disabled.
  Fast, disposable, data resets on restart.
- **`prod` profile:** PostgreSQL, **Flyway** runs `db/migration/V*.sql`, then
  Hibernate `ddl-auto: validate` only. The datasource comes entirely from the
  environment. Activated by the `docker-compose.postgres.yml` overlay.

## Consequences

- Schema changes are explicit, reviewable, versioned SQL.
- Startup fails loudly if the entities and the migrated schema disagree.
- Same application image in both modes — only the profile and env change.
