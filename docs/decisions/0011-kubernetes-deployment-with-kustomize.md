# 0011 — Kubernetes deployment with Kustomize, config from the platform

**Status:** accepted · no cluster is live yet; manifests validate with `kubectl --dry-run`

## Context

The stack runs locally on Docker Compose. Getting it onto a managed cluster needs
manifests, a config story that isn't "mount a git repo", and a secrets model a
reviewer can trust. No cloud account is wired up, so whatever lands has to be
verifiable offline.

## Decision

- **Kustomize, one base + two overlays** (`deploy/k8s/`). The base is the whole
  system in namespace `asset-tracker`; `overlays/local` side-loads images and runs
  data services in-cluster; `overlays/cloud` swaps GHCR images, deletes the
  in-cluster Postgres/RabbitMQ, adds replicas and TLS.
- **The ConfigMap/Secret is the configuration source on Kubernetes — config-server
  is not deployed there.** Each image already carries its `application-prod.yml`;
  the platform supplies the environment. Spring Cloud Config stays in the Compose
  stack for the non-container path. This removes the config-repo volume mount and
  the "clone a repo at boot" failure mode.
- **One datasource knob.** `PG_HOST`/`PG_PORT`/`PG_OPTS` in the ConfigMap; every
  JPA Deployment composes `jdbc:postgresql://$(PG_HOST):$(PG_PORT)/<db>$(PG_OPTS)`.
  Moving to a managed database is a one-line overlay change.
- **No stored cloud credentials.** CI/CD authenticates by GitHub OIDC federation;
  in-cluster, the Secret `asset-tracker-db` is delivered by the External Secrets
  Operator from Key Vault / Secrets Manager via workload identity. The committed
  Secret is a labelled stub for local clusters only.
- **Probes and limits on every service:** `startupProbe` + `readinessProbe` +
  `livenessProbe` on `/actuator/health`, CPU/memory requests and limits.

## Consequences

- `kubectl kustomize deploy/k8s/overlays/<local|cloud>` renders; both pass
  `kubectl apply --dry-run=client`. A real apply still needs a cluster, an ingress
  controller, a `StorageClass`, and the `REPLACE_WITH_*` values filled in.
- Two config mechanisms exist (Config Server for Compose, ConfigMap for k8s). The
  service YAML tolerates both because everything is already `${ENV:default}`.
- config-server has no k8s manifest; if it's ever needed there (dynamic refresh),
  it comes back as a Deployment with the config-repo baked into its image.
- Kustomize `$patch: delete` in the cloud overlay is verbose but keeps the base a
  single complete definition of the system.
