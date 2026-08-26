# Container view

What is deployed, what talks to what, and where credentials come from.

```mermaid
flowchart TB
  Agent["Service agent / admin<br/>browser"]

  subgraph Cluster["k3s cluster, namespace crm"]
    Ing["Ingress (Traefik)<br/>crm-api.localtest.me"]
    Svc["Service crm-api<br/>80 to 8080"]
    Pod["Deployment crm-api<br/>non-root UID 10001, three probes"]
    CM["ConfigMap crm-api-config<br/>DB host, port, name, user"]
    Sec["Secret crm-api-secrets<br/>DB password, JWT secret"]
  end

  DB[("PostgreSQL 17")]
  Kafka[["Kafka 4.3.1<br/>crm.interaction.v1"]]
  DLT[["crm.interaction.v1.DLT"]]

  Agent -->|HTTPS / REST + JWT| Ing
  Ing --> Svc
  Svc --> Pod
  CM -- env --> Pod
  Sec -- env --> Pod
  Pod -->|JDBC| DB
  Pod -->|publish| Kafka
  Kafka -->|consume| Pod
  Kafka --> DLT
```

## Units

| Container | What it is | Notes |
| --------- | ---------- | ----- |
| React frontend | Vite, Node 22 | Sets `X-Correlation-ID` on every call |
| Spring Boot API | Java 21, one jar | JWT filter, service layer, JPA, Kafka producer and consumer |
| PostgreSQL | `postgres:17` | Schema owned by Flyway |
| Kafka | `apache/kafka:4.3.1` | Topic `crm.interaction.v1`, dead letter `.DLT` |

## Configuration and secrets

Nothing that is secret is in a file git can see.

| Value | Comes from |
| ----- | ---------- |
| DB host, port, name, user | ConfigMap `crm-api-config` |
| DB password, JWT secret | Secret `crm-api-secrets`, created from the command line |
| Local development | gitignored `.env`, loaded by `spring.config.import` |

`k8s/examples/secret.example.yaml` records which keys the Secret must carry. It
lives under `examples/` because `kubectl apply -f k8s/` would otherwise apply it
and overwrite a working Secret with placeholders.

## Profiles

| Profile | Database |
| ------- | -------- |
| `local` (default) | PostgreSQL via `.env` |
| `azure` | Azure PostgreSQL Flexible Server |
| `test` | H2 in PostgreSQL mode, plus Testcontainers for the integration suite |

## Deploy path

`git push` to GitHub Actions, which builds and tests, builds the image, validates
the manifests, then creates a cluster and deploys them. See
`.github/workflows/ci.yml` and `docs/rollback-runbook.md`.
