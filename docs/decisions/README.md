# Architecture Decision Records

Short records of the choices that would otherwise look arbitrary. Format:
Status / Context / Decision / Consequences.

| # | Decision |
|---|---|
| [0001](0001-monorepo-gradle-multiproject.md) | One monorepo, one Gradle multi-project build |
| [0002](0002-hs256-jwt-with-tenancy-claims.md) | HS256 JWT with `role` + `clientIds` claims for v1 |
| [0003](0003-synchronous-orchestration.md) | Synchronous orchestration + independent transactions |
| [0004](0004-asset-service-owns-custody-state.md) | asset-service owns live custody; assignment-service owns history |
| [0005](0005-public-reads-authenticated-writes.md) | Public reads, authenticated writes |
| [0006](0006-flyway-owns-the-schema.md) | Flyway owns the schema under the `prod` profile |
| [0007](0007-tags-identify-slots-not-units.md) | An asset tag identifies a slot, not a physical unit |
| [0008](0008-rs256-jwt-with-jwks.md) | RS256 JWT validated against a JWKS endpoint |
| [0009](0009-resilience4j-on-the-orchestrator.md) | Resilience4j retry + circuit breaker on the asset-service call |
| [0010](0010-event-driven-notifications.md) | Event-driven notifications over RabbitMQ |
