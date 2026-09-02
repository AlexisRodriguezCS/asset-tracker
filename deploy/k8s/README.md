# Kubernetes manifests

Kustomize layout for running the whole stack on a cluster. One base, two overlays.

```
deploy/k8s/
  base/                 every service + Postgres + RabbitMQ + Ingress, namespace asset-tracker
  overlays/local/       kind / minikube / Docker Desktop - images side-loaded, in-cluster data
  overlays/cloud/       AKS / EKS / GKE - GHCR images, managed Postgres + broker, 2 replicas, TLS
```

## What's in the base

| Object | Notes |
| --- | --- |
| `discovery-server` | Eureka registry (1 replica) |
| `api-gateway` | the only ingress backend; `/api/**` -> `lb://<service>` |
| `auth-service` | RS256 signer, serves `/.well-known/jwks.json` |
| `client / people / location / asset / assignment -service` | JPA services, one Postgres DB each |
| `notification-service` | RabbitMQ consumer, no DB |
| `postgres` | single replica + 2Gi PVC, `init.sql` creates the six databases |
| `rabbitmq` | single replica + 1Gi PVC, management UI on 15672 |
| `asset-tracker-env` ConfigMap | Eureka URL, `SPRING_PROFILES_ACTIVE=prod`, JWT issuer/JWKS, `PG_HOST` |
| `asset-tracker-db` Secret | **stub** username/password - replaced in cloud by External Secrets |
| `asset-tracker` Ingress | host `asset-tracker.local` -> `api-gateway:8080` |

`config-server` is intentionally **not** deployed here: on Kubernetes the ConfigMap/Secret are
the configuration source, and each image already carries its `application-prod.yml`. Spring Cloud
Config still runs in the Docker Compose stack.

Every service Deployment has CPU/memory requests+limits and `startupProbe` + `readinessProbe` +
`livenessProbe` on `/actuator/health`.

## Datasource wiring

`PG_HOST` / `PG_PORT` / `PG_OPTS` live in the ConfigMap. Each JPA Deployment builds its own URL:

```
jdbc:postgresql://$(PG_HOST):$(PG_PORT)/<db>$(PG_OPTS)
```

so switching to a managed database is a one-line ConfigMap change (the cloud overlay does exactly
that and deletes the in-cluster `postgres`).

## Local run

```bash
# 1. build images from the compose file
docker compose -f infra/compose/docker-compose.yml build

# 2. load them into the cluster (kind shown; minikube: `minikube image load ...`)
for s in discovery-server api-gateway auth-service client-service people-service \
         location-service asset-service assignment-service notification-service; do
  kind load docker-image "assettracker/$s:latest"
done

# 3. apply
kubectl apply -k deploy/k8s/overlays/local

# 4. watch it come up
kubectl -n asset-tracker get pods -w
```

Reach it via the ingress (`echo "127.0.0.1 asset-tracker.local" | sudo tee -a /etc/hosts` once an
ingress controller is installed) or a quick port-forward:

```bash
kubectl -n asset-tracker port-forward svc/api-gateway 8080:8080
```

## Validate without a cluster

```bash
kubectl kustomize deploy/k8s/overlays/local | kubectl apply --dry-run=client -f -
kubectl kustomize deploy/k8s/overlays/cloud | kubectl apply --dry-run=client -f -
```

## Cloud

See [`docs/deployment.md`](../../docs/deployment.md) for the managed-service mapping, the
no-stored-credentials secrets model, and the `deploy.yml` workflow.
