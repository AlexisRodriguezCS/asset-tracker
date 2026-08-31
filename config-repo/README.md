# config-repo

Configuration files served by [`config-server`](https://github.com/ar-ecommerce-platform/config-server)
to the rest of the [ar-ecommerce-platform](https://github.com/ar-ecommerce-platform).

## Layout

```
config-repo/
├── application.yml                       # global — merged into every service (logging, actuator, eureka defaults)
├── auth-service/
│   ├── auth-service.yml                  # default profile
│   ├── auth-service-dev.yml              # dev overrides (H2)
│   └── auth-service-prod.yml             # prod overrides (Postgres, RabbitMQ)
├── api-gateway/api-gateway.yml
├── product-service/product-service.yml
├── inventory-service/inventory-service.yml
├── order-service/order-service.yml
├── payment-service/payment-service.yml
└── notification-service/notification-service.yml
```

## How it's served

`config-server` runs with a **native** backend (`spring.cloud.config.server.native.search-locations`)
pointed at this directory — a sibling checkout for local dev, a mounted volume in Docker. It is
**not** the Git backend and does not clone anything.

```
GET http://localhost:8888/{application}/{profile}
```

merges, lowest priority first:

1. `application.yml` (global)
2. `{application}/{application}.yml`
3. `{application}/{application}-{profile}.yml` (if a profile is active)

## Important: not load-bearing

Every service also ships a **self-contained `application.yml`** and imports from config-server as
`optional:configserver:...`. Environment variables (injected by `infra/compose/.env` / the
deployment) override anything served from here. So this repo is a demonstration of centralized
config, not a single point of failure.

## Secrets

No secrets live in this repo. Placeholders like `${AUTH_DB_USERNAME}` are resolved from the
environment at runtime. See the platform secrets model in
[infra/RUNBOOK.md](https://github.com/ar-ecommerce-platform/infra/blob/main/RUNBOOK.md).

## Related

[infra](https://github.com/ar-ecommerce-platform/infra) ·
[config-server](https://github.com/ar-ecommerce-platform/config-server) ·
[discovery-server](https://github.com/ar-ecommerce-platform/discovery-server) ·
[api-gateway](https://github.com/ar-ecommerce-platform/api-gateway)
