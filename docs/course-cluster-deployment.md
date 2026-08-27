# Course-cluster deployment record

Deployed 2026-08-27 into namespace `student02` on the shared k3s cluster
(swe2, `100.22.136.97`). This is the record of what is running, how it got
there, and how to see it — the executed counterpart to the reasoning in
`k3s-deploy-plan.md`.

## What is running

| Field | Value |
| ----- | ----- |
| URL | `http://crm-student02.100.22.136.97.nip.io` |
| Namespace | `student02` |
| Image | `ghcr.io/pupilliluke/crm-api@sha256:92d02fdfbcf9e51b1d7c9a57e00aaddd45eb5e0000b544ff51555ae954b93566` |
| Built from | commit `d0257f55b7d3962131cb912ce102ffbaef4e5a70` (develop; read from the image's `org.opencontainers.image.revision` label) |
| Cluster | k3s `v1.36.2+k3s1` (upgraded in the ~21 July rebuild) |
| Database | `bootcamp` on the same host, schema `student02`, connected with an explicit `?currentSchema=student02` |
| Kafka | topic `student02.crm.interaction.v1`, group `student02.crm-interaction-service-v1`, both namespace-prefixed |

The digest, not the `develop` tag, is what the Deployment runs — the tag is a
mutable pointer and the digest is the build that was verified. The GHCR
package is public, so no image pull secret is needed. Note that `main`'s
image digest lags develop; a demo should deploy the digest that contains the
topic-prefix configuration, which as of this writing means develop's.

## How it was deployed

The manifests in `k8s/` are team-generic on purpose; everything per-student
is applied as a patch afterwards, exactly as the comments in
`k8s/configmap.yaml` describe. `namespace.yaml` and `test/postgres.yaml` are
never applied here — the namespace and PostgreSQL are provided.

```
$env:KUBECONFIG = "C:\Users\<you>\.kube\student02.yaml"

# 1. Secret, out of band, values from the credentials sheet — never a file
kubectl -n student02 create secret generic crm-api-secrets `
  --from-literal=LOCAL_DB_PASSWORD='<sheet>' `
  --from-literal=JWT_SECRET='<generated, 32+ chars>'

# 2. The shared manifests -- the cluster ConfigMap overlay, not the local one:
#    k8s/configmap.yaml keeps the k3d/CI values and stays under test on every
#    pull request; k8s/cluster/configmap.yaml carries the course endpoints.
kubectl -n student02 apply -f k8s/cluster/configmap.yaml -f k8s/service.yaml `
  -f k8s/deployment.yaml -f k8s/ingress.yaml

# 3. Per-student values
kubectl -n student02 patch configmap crm-api-config --type merge -p '{"data":{
  "LOCAL_DB_USER":"student02",
  "LOCAL_DB_URL_OPTIONS":"?currentSchema=student02",
  "CRM_INTERACTION_TOPIC":"student02.crm.interaction.v1",
  "CRM_CONSUMER_GROUP":"student02.crm-interaction-service-v1"}}'
kubectl -n student02 patch ingress crm-api --type merge -p '{"spec":{"rules":[{
  "host":"crm-student02.100.22.136.97.nip.io","http":{"paths":[{"path":"/",
  "pathType":"Prefix","backend":{"service":{"name":"crm-api","port":{"number":80}}}}]}}]}}'

# 4. Pin the published digest (the manifest's crm-api:dev cannot be pulled here)
kubectl -n student02 set image deploy/crm-api `
  crm-api=ghcr.io/pupilliluke/crm-api@sha256:92d02fdf...

kubectl -n student02 rollout status deploy/crm-api --timeout=300s
```

### Repeatable deploys

The sequence above is scripted: `bash k8s/cluster-deploy.sh` reads the
per-student values from a gitignored `.env.cluster` at the repository root
(template: `k8s/cluster.env.example` — copy it there and fill your row of
the credentials sheet), so four students deploy four namespaces from one
repository with no tracked file carrying anyone's identity. The Secret is
created only if absent — rotating it is a deliberate delete-and-rerun,
never an accidental overwrite of a working credential. Two lessons from the
script's first runs are encoded in it: `KUBECONFIG_PATH` must use forward
slashes (backslashes do not survive bash sourcing, after which kubectl
silently dials `localhost:8080`), and the final ingress check polls rather
than sampling once, because Traefik rebuilds its router asynchronously
after the host patch.

The ingress host is nip.io: `crm-student02.100.22.136.97.nip.io` resolves to
`100.22.136.97` from anywhere with no DNS setup, and Traefik routes on the
Host header, so each student can mint a hostname without touching shared
config. The course material names no hostname scheme; this one was
self-chosen and flagged to the instructor in case an official scheme exists.

`LOCAL_DB_URL_OPTIONS` makes the JDBC URL match the credentials sheet
byte-for-byte. The role's default `search_path` already lands connections in
the right schema, so this is belt and braces — but on a shared database,
explicit beats implicit.

### Kubeconfig hygiene

The cluster was rebuilt around 21 July and every credential issued before
that died with it — CA, server cert, and service-account token together.
Before trusting any newly issued kubeconfig, check it pins the current CA
and a fresh signing key:

```
grep 'certificate-authority-data' student02.yaml | sed 's/.*: *//' | base64 -d \
  | openssl x509 -noout -subject -dates
```

Expected subject: `CN=k3s-server-ca@1784642847` (the pre-rebuild CA was
`@1784129564`; a file carrying it fails TLS against the live server, which is
the verification working, not a bug — never "fix" it with
`--insecure-skip-tls-verify`).

## Verification record (2026-08-27)

Every check below ran against the live deployment through the public
ingress, not port-forwarding:

- readiness `200` through the ingress; the readiness group includes `db`, so
  this also proves pods reach PostgreSQL on the host's public IP from inside
  the cluster (the hairpin question answered itself)
- `admin1` login issued a token; authenticated `GET /api/customers` returned
  CUS-1001 and CUS-1002; anonymous read refused with `401`
- the pod's consumer owns partition 0 of `student02.crm.interaction.v1`
- an interaction posted with `X-Correlation-Id: deploy-verify-001` returned
  `202` and the *pod's* consumer logged `Processed interaction event` with
  the same correlation id about one second later
- an interaction recorded through the React UI ("✓ Saved and published")
  produced the same paired log lines, correlation id generated by the
  frontend — UI → proxy → pod → PostgreSQL + Kafka → consumer, one id
  throughout
- the full `OWN_CLUSTER=0` smoke suite passed, including the deliberate
  break and rollback — evidence recorded in `rollback-runbook.md`

### One incident worth keeping

While the laptop instance from local testing was still running, the deployed
pod joined the consumer group and was assigned **no partitions** — the
laptop held partition 0. This is precisely the group-splitting failure the
comments in `application.yml` warn about, observed live: same group id, two
applications, silent partial consumption. Stopping the laptop instance
rebalanced partition 0 to the pod within seconds. Moral: one consumer group
name means one logical application; a dev instance pointed at shared
infrastructure is a member of production.

## Viewing the product

The product is deliberately two surfaces. The backend lives on the cluster;
the UI runs on a laptop and proxies to it. Students have no SSH — `kubectl`
against the API at `:6443` *is* the terminal on the backend.

**UI.** `vite.config.ts` reads `VITE_PROXY_TARGET` (default
`http://localhost:8080`). Create a gitignored `frontend/.env.local`:

```
VITE_PROXY_TARGET=http://crm-student02.100.22.136.97.nip.io
```

then `npm run dev` in `frontend/` and open `http://localhost:5173`. The
browser talks only to localhost; Vite forwards `/api` server-side, so CORS
never enters the picture. Demo logins are the seeded `agent1`/`agent1` and
`admin1`/`admin1`.

**Terminals** (set `KUBECONFIG` once per shell, or pass `--kubeconfig`):

```
kubectl -n student02 get pods -w                  # live pod view
kubectl -n student02 logs -f deploy/crm-api       # correlation ids live
kubectl -n student02 get deploy,svc,ingress -o wide
kubectl -n student02 rollout history deploy/crm-api
psql -h 100.22.136.97 -U student02 -d bootcamp    # password from the sheet
docker run --rm edenhill/kcat:1.7.1 -b 100.22.136.97:9092 \
  -t student02.crm.interaction.v1 -C -o beginning # every event, as JSON
```

Recording an interaction in the UI while `logs -f` runs shows the same
correlation id on the HTTP request and the Kafka consumer line — the
Verifier's screen for the demo.

## Who can reach what

- The Vite dev server binds to localhost only (`Network: use --host to
  expose`). The UI is private to the machine running it.
- The API is shared: anyone whose IP passes the firewall can reach the
  ingress, log in with the seeded accounts, and register new (disabled)
  accounts. Teammates can point their own frontend at the URL above with the
  one-line `.env.local`.
- Whether "anyone" means the internet or class IPs only is not documented
  for ports 80/443 — the setup notes state "class IPs only" for PostgreSQL
  and Kafka and are silent on the ingress. The evidence leans open: every
  port, PostgreSQL included, answers from a home IP that was never
  registered anywhere, so the restriction is either far broader than it
  sounds or not in effect. Treat the whole box as internet-reachable until
  a phone-on-cellular test of the readiness URL says otherwise.
- Stakes are low by design: synthetic data only, demo credentials are
  intentionally public in the source, the JWT secret is generated and lives
  only in the cluster Secret, and the DB password is not reachable through
  the application.

## Demo-day pre-flight (from the venue network)

Everything in the live demo — UI proxy, kubectl, psql, kcat — depends on the
conference room's IP being allowed through the firewall. During the reset
window before the slot:

1. `curl http://crm-student02.100.22.136.97.nip.io/actuator/health/readiness`
   → must be `200`. If not, switch to the fallback screenshots immediately.
2. `kubectl -n student02 get pods` → `1/1 Running`
3. Log into the UI once end-to-end
4. Have `logs -f` and psql sessions already open in terminals

## Known drift and open follow-ups

- **kubectl skew**: Docker Desktop ships kubectl `v1.30.5`; the course
  server is `v1.36.2` — outside the supported ±1 skew. Every command prints
  a warning; everything used here works. Closing it means putting a newer
  kubectl ahead of Docker's on PATH.
- **CI version pins**: the `manifests` job validates against Kubernetes
  `1.35.5` and the CI/k3d clusters run `v1.35.5-k3s1`, matching the course
  cluster *before* its rebuild. It now runs `v1.36.2+k3s1`. Nothing broke,
  but the pins no longer describe the target and are worth bumping together.
- **Version-to-version rollback**: the runbook proves a bad rollout is
  survivable; a rollback between two *working* digests is now possible
  (publish pushes per-commit images) and still unrehearsed.
- **This branch's `application-local.yml`** predates the
  `${LOCAL_DB_URL_OPTIONS:}` hook that develop (and therefore the deployed
  image) has — a local run from this branch ignores that variable until
  develop is merged in.
