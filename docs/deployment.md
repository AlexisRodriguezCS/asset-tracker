# Deployment

How asset-tracker goes from a laptop to a managed cluster. Nothing is deployed to
a cloud yet — this describes the target and ships the manifests that get there.
See [ADR 0011](decisions/0011-kubernetes-deployment-with-kustomize.md) for the
reasoning.

## Three ways to run it

| | Orchestrator | Config source | Data services | Images |
| --- | --- | --- | --- | --- |
| **Compose** (today) | `infra/compose/docker-compose.yml` | Spring Cloud Config (`config-server`) | in-network containers | built locally |
| **Local k8s** | `deploy/k8s/overlays/local` | ConfigMap + Secret | in-cluster pods + PVCs | side-loaded into kind/minikube |
| **Cloud k8s** | `deploy/k8s/overlays/cloud` | ConfigMap + External Secrets | managed Postgres + broker | GHCR, by commit SHA |

The service YAML is identical across all three — every value is `${ENV:default}`,
so only the source of the environment changes.

## Target topology (cloud)

```
                 Internet
                    │  HTTPS  console.asset-tracker.example
             ┌──────▼───────┐
             │   Ingress    │  (nginx / AGIC)  TLS from cert-manager or Key Vault
             └──────┬───────┘
                    │
             ┌──────▼───────┐     service discovery
             │ api-gateway  │◄──────────────┐
             │  (2 replicas)│               │
             └──────┬───────┘        ┌──────┴───────┐
        /api/**  route by path       │ discovery-   │
                    │                │ server       │
   ┌────────┬───────┼───────┬────────┴──┬───────────┴─┐
   ▼        ▼       ▼       ▼           ▼             ▼
 auth    client  people  location    asset      assignment ──publish──┐
   │        │       │       │           │             │               │
   └────────┴───────┴───────┴───────────┘        ┌────▼─────┐    ┌─────▼──────┐
        managed PostgreSQL (one DB each)         │ managed  │    │notification│
        Azure DB for PostgreSQL Flexible Server  │ broker   │◄───┤  -service  │
        / Amazon RDS for PostgreSQL              │ (AMQP)   │    └────────────┘
                                                 └──────────┘
```

`auth-service` also serves `/.well-known/jwks.json`; the gateway validates RS256
tokens against it. `config-server` is not part of the cluster (see ADR 0011).

## Managed-service mapping

| In-cluster (local) | Azure | AWS |
| --- | --- | --- |
| `postgres` Deployment + PVC | Azure DB for PostgreSQL Flexible Server | RDS for PostgreSQL |
| `rabbitmq` Deployment + PVC | Azure Service Bus (AMQP 1.0) or a RabbitMQ cluster on AKS | Amazon MQ for RabbitMQ |
| images built locally | GHCR (current) or Azure Container Registry | GHCR or Amazon ECR |
| stub `asset-tracker-db` Secret | Azure Key Vault + External Secrets Operator | Secrets Manager + External Secrets Operator |
| `nginx` Ingress | AGIC or ingress-nginx on AKS | AWS Load Balancer Controller |
| n/a | Azure Monitor / Container Insights | CloudWatch Container Insights |

Switching Postgres over is one line in `overlays/cloud`: `PG_HOST` →
the managed FQDN, `PG_OPTS` → `?sslmode=require`. The overlay then deletes the
in-cluster `postgres` and `rabbitmq` objects.

The six databases (`authdb`, `clientdb`, `peopledb`, `locationdb`, `assetdb`,
`assignmentdb`) must exist on the managed server before rollout — Flyway creates
tables, not databases. Provision them with the server (Bicep/Terraform) or a
one-off `psql`.

## Secrets: nothing stored, anywhere

| Layer | Where the secret lives | How the workload proves who it is |
| --- | --- | --- |
| Local dev | gitignored `infra/compose/.env` from `.env.example` | n/a |
| CI (`ci.yml`) | GitHub `GITHUB_TOKEN` for GHCR only | — |
| CD (`deploy.yml`) | nothing — **OIDC federation** to Azure/AWS | GitHub's OIDC token, trusted by a federated credential scoped to this repo + environment |
| In-cluster | Key Vault / Secrets Manager, surfaced as `asset-tracker-db` by External Secrets Operator | **workload identity** (AKS pod identity / IRSA) — no key on disk |
| Token signing | ephemeral RSA keypair in `auth-service` today; Key Vault / KMS later (sign in the HSM) | workload identity |

There is no long-lived cloud credential in the repo, in GitHub secrets, or in the
cluster. The committed `asset-tracker-db` Secret holds `att` / `att-dev-password`
and is for local clusters only — the cloud overlay deletes it.

### One-time setup for OIDC federation (Azure)

1. Create an app registration / user-assigned managed identity.
2. Add a **federated credential**: issuer `https://token.actions.githubusercontent.com`,
   subject `repo:AlexisRodriguezCS/asset-tracker:environment:production`.
3. Grant it `Azure Kubernetes Service Cluster User` on the cluster and `get` on the
   Key Vault secrets.
4. Set repo variables `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, `AZURE_SUBSCRIPTION_ID`,
   `AKS_RESOURCE_GROUP`, `AKS_CLUSTER_NAME`.

`.github/workflows/deploy.yml` then runs with `permissions: id-token: write` and no
stored password.

## Rollout

`deploy.yml` (`workflow_dispatch`) takes an `image_tag` (a commit SHA already built
by `ci.yml`), federates in, pins every image to that SHA, and:

```
kubectl apply -k deploy/k8s/overlays/cloud
kubectl -n asset-tracker rollout status deployment/api-gateway
```

Order takes care of itself: Eureka clients retry registration, JPA services retry
the datasource, and the RabbitMQ listener has `missing-queues-fatal: false`. The
probes hold traffic until each service reports healthy.

## Validate the manifests without a cluster

```bash
kubectl kustomize deploy/k8s/overlays/local | kubectl apply --dry-run=client -f -
kubectl kustomize deploy/k8s/overlays/cloud | kubectl apply --dry-run=client -f -
```

Both render and pass client-side validation today. A real apply additionally needs
a running cluster, an ingress controller, a default `StorageClass`, and the
`REPLACE_WITH_*` values in `overlays/cloud` filled in.

## Still to do

- Terraform/Bicep for the cluster, the Postgres server + its six databases, the
  broker, Key Vault, and the federated credential.
- External Secrets Operator `SecretStore` + `ExternalSecret` manifests.
- `HorizontalPodAutoscaler` and `PodDisruptionBudget` per service.
- Ship metrics/traces to a managed backend (ADR roadmap item 4).
- Back the token-signing key with Key Vault / KMS (ADR roadmap item 2).
