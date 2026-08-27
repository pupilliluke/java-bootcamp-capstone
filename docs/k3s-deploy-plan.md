# Deploying to the shared k3s cluster

What has to change to move from local k3d to the course cluster, and why.

**Status: executed 2026-08-27.** Every step below was carried out against
namespace `student02`; the record of what is running, the exact commands,
the verification evidence, and the open follow-ups live in
`course-cluster-deployment.md`. The one deviation from "ask before
guessing": the ingress hostname scheme was still undocumented at deploy
time, so `crm-student02.100.22.136.97.nip.io` was self-chosen via nip.io and
flagged to the instructor.

## The environment

Everything runs on one shared AWS EC2 box in `us-west-2`. There is no AWS
console or CLI step; you reach it with `kubectl` and your kubeconfig.

| Service | Endpoint |
| ------- | -------- |
| Kubernetes API | `https://<host>:6443` |
| PostgreSQL 17 | `<host>:5432`, database `bootcamp`, schema per student |
| Kafka 4.0 | `<host>:9092`, single broker, shared by the cohort |
| Ingress | Traefik on `:80` and `:443` |
| Image registry | GHCR |

Two cohorts exist with different hosts. `kubeconfigs (swe1)` points at
`100.23.237.198`, `kubeconfigs (swe2)` at `100.22.136.97`. Use whichever the
credentials sheet assigns; both API ports answer.

You get admin rights inside your own namespace and nothing outside it.

## Connecting

Copy the kubeconfig somewhere without spaces in the path. The provided folder is
`kubeconfigs (swe1)`, and Windows kubectl cannot read a path with spaces and
parentheses. It fails back to `localhost:8080`, which looks like a certificate or
network problem and is not.

```
$env:KUBECONFIG = "C:\Users\lukel\.kube\studentNN.yaml"
kubectl get pods
```

`kubectl get nodes` returns Forbidden. That is correct: the account is scoped to
its namespace. Namespace-level calls work.

## What has to change in k8s/

| File | Change |
| ---- | ------ |
| `namespace.yaml` | **Do not apply.** The namespace already exists and we cannot create one |
| `test/postgres.yaml` | **Do not apply.** PostgreSQL is provided |
| `configmap.yaml` | namespace, and every endpoint value |
| `deployment.yaml` | namespace, and the image reference |
| `service.yaml` | namespace only |
| `ingress.yaml` | namespace and host |

### ConfigMap

| Key | Local now | Cluster |
| --- | --------- | ------- |
| `LOCAL_DB_HOST` | `host.k3d.internal` | the course host |
| `LOCAL_DB_NAME` | `crm` | `bootcamp` |
| `LOCAL_DB_USER` | `crm` | `studentNN` |
| `KAFKA_BOOTSTRAP_SERVERS` | `host.k3d.internal:9092` | `<host>:9092` |

`host.k3d.internal` is a k3d invention that resolves to the laptop. It does not
exist on the course cluster.

### Schema

Each role's default `search_path` is its own schema, so Flyway should create
tables in the right place without help. `application-local.yml` builds the URL as

```
jdbc:postgresql://${LOCAL_DB_HOST}:${LOCAL_DB_PORT}/${LOCAL_DB_NAME}
```

with no `currentSchema`. Relying on the default works; being explicit is safer,
and would mean adding an optional parameter to that URL.

### Image

`crm-api:dev` only exists in a local Docker daemon. The cluster cannot pull it,
and `k3d image import` has no equivalent here. The image has to come from GHCR,
which the setup notes name as the registry for this environment.

Make the package public and no pull secret is needed. Keep it private and the
namespace needs an image pull secret.

This is also what closes the digest gap in ADR-005: a pushed image has a real
`RepoDigest`, so `deployment.yaml` can pin `@sha256:` instead of a tag.

### Ingress

`crm-api.localtest.me` resolves to `127.0.0.1` by design and is meaningless on a
shared cluster. The host has to be something Traefik can route on the class
network. The scheme is not documented in the material we hold, so ask before
guessing.

### Kafka topic

The broker is shared and the setup notes say there is **no per-user topic
isolation**. Our topic is `crm.interaction.v1`, which every other team building
this CRM will also use. Consumers would read each other's events.

Prefix it per student, for example `studentNN.crm.interaction.v1`. The name is a
constant in `InteractionEventProducer` and would be better as configuration.

## Secret

Created out of band, same as locally, and we have the rights:

```
kubectl -n studentNN create secret generic crm-api-secrets \
  --from-literal=LOCAL_DB_PASSWORD='...' \
  --from-literal=JWT_SECRET='...'
```

Never `kubectl apply -f k8s/examples/secret.example.yaml`.

## Order

1. Confirm cohort and student number from the credentials sheet
2. Copy the kubeconfig to a space-free path and confirm `kubectl get pods` works
3. Push the image to GHCR
4. Parameterise the Kafka topic
5. Repoint the ConfigMap
6. Change the namespace in four files
7. Create the Secret
8. Ask about the ingress host scheme
9. Apply, then run the smoke checks

## What smoke.sh needs

`k8s/smoke.sh` assumes it owns the cluster. On a shared one, three things are
untrue:

- it applies `namespace.yaml` and `test/postgres.yaml`, neither of which we may
  or should apply
- it patches the ConfigMap to point at an in-cluster `postgres` Service that will
  not exist
- it reaches the ingress at `localhost:8088`, a k3d port mapping

Steps 5 and 6, the deliberate break and the rollback, work unchanged: they are
`kubectl set image` and `kubectl rollout undo` inside our own namespace. Those
are the parts worth keeping, and the rehearsal evidence in
`docs/k8s-ci-evidence.txt` stays valid for the CI cluster either way.
