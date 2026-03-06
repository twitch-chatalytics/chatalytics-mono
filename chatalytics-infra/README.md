# Chatalytics Kubernetes Deployment

Deploys chatalytics to the K3s cluster: Kafka, Redis, and four Spring Boot services.

## Architecture

```
                   ┌──────────────────────────────────────────┐
                   │           chatalytics namespace           │
                   │                                          │
                   │  ┌─────────┐       ┌─────────┐          │
                   │  │  Kafka  │       │  Redis  │          │
                   │  │  :9092  │       │  :6379  │          │
                   │  └────┬────┘       └────┬────┘          │
                   │       │                 │               │
                   │  ┌────┴─────────────────┴────┐          │
                   │  │           API              │          │
                   │  │  (Liquibase + REST :8080)  │          │
                   │  └───────────────────────────-┘          │
                   │                                          │
                   │  ┌───────────────┐  ┌──────────────┐    │
                   │  │ Session Mgr   │  │ IRC Consumer │    │
                   │  │    :8080      │  │  (Kafka→PG)  │    │
                   │  └───────┬───────┘  └──────────────┘    │
                   │          │                               │
                   │  ┌───────┴───────┐                      │
                   │  │ IRC Producer  │                      │
                   │  │ (Twitch→Kafka)│                      │
                   │  └───────────────┘                      │
                   └──────────────────────────────────────────┘
                                      │
                   ┌──────────────────┴───────────────────────┐
                   │     postgresql namespace (shared)         │
                   │     postgres-pooler-rw:5432               │
                   └──────────────────────────────────────────┘
```

## Prerequisites

**PostgreSQL database** must exist before deploying. Connect via pgAdmin at `http://192.168.1.205` (admin@local.domain / admin) and run:

```sql
CREATE USER chatalytics WITH PASSWORD 'omnicx8216';
CREATE DATABASE chatalytics OWNER chatalytics;
GRANT ALL PRIVILEGES ON DATABASE chatalytics TO chatalytics;
```

**Docker images** must be pushed to Docker Hub. From the repo root:

```bash
DOCKER_USERNAME=peavers DOCKER_TOKEN=<token> \
  ./gradlew :api:jib :session-manager:jib :irc-producer:jib :irc-consumer:jib
```

## Deploy

```bash
./install.sh
```

Deploys in order: namespace/secrets, Kafka+Redis, API (runs Liquibase), session-manager, IRC producer+consumer.

## Tear Down

```bash
./uninstall.sh
```

Deletes the `chatalytics` namespace and everything in it. The PostgreSQL database is unaffected.

## Common Commands

```bash
# Status
kubectl get pods -n chatalytics

# Logs
kubectl logs -n chatalytics deployment/api --tail=50
kubectl logs -n chatalytics deployment/session-manager --tail=50
kubectl logs -n chatalytics deployment/irc-producer --tail=50
kubectl logs -n chatalytics deployment/irc-consumer --tail=50

# Restart a service
kubectl rollout restart deployment/api -n chatalytics

# Restart everything
kubectl rollout restart deployment -n chatalytics

# Watch pods
kubectl get pods -n chatalytics -w
```

## Structure

```
chatalytics-infra/
├── base/
│   └── namespace.yaml            # chatalytics namespace
├── secrets/
│   ├── imagepullsecret.yaml      # Docker Hub credentials
│   └── secrets.yaml              # App config (DB, Kafka, Redis, Twitch, IRC)
├── infrastructure/
│   ├── kafka.yaml                # StatefulSet + Service, KRaft mode, 10Gi PVC
│   └── redis.yaml                # StatefulSet + Service, AOF persistence, 2Gi PVC
├── services/
│   ├── api.yaml                  # Deployment + Service (runs Liquibase migrations)
│   ├── session-manager.yaml      # Deployment + Service
│   ├── irc-producer.yaml         # Deployment (outbound only)
│   └── irc-consumer.yaml         # Deployment (Kafka consumer only)
├── install.sh                    # Ordered deploy script
├── uninstall.sh                  # Namespace teardown script
└── README.md
```

## Notes

- JVM services take ~100-120s to start. Startup probes allow up to 230s before killing.
- Kafka topics (`raw-messages`, `raw-sessions-online`, `raw-sessions-offline`) are auto-created.
- All services use a shared Secret via `envFrom` with Spring Boot relaxed binding.
- Images use `:latest` tag with `imagePullPolicy: Always`.
