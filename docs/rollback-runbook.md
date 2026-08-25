# Rollback runbook

Ported from the lab 44 runbook and re-grounded in this repository. Every command
below was run against the real cluster, and the observed output is recorded at
the bottom rather than described from memory.

## Known-good identity

Recorded **before** a deploy, not looked up during an incident. Update this table
as part of promoting, or it is worth nothing when it matters.

| Field | Value |
| ----- | ----- |
| Cluster | `k3d-lab42`, namespace `crm` |
| Deployment | `crm-api` |
| Image | `crm-api:dev` |
| Revision label | `a66237c06d3fd977d05411af7c97998b7fc63447` (`org.opencontainers.image.revision`) |
| Ingress | `crm-api.localtest.me` via Traefik on `:8088` |
| Verification | readiness, then login, then an authenticated `GET /api/customers` |

The image label is how a running pod is traced back to a commit:

```
kubectl -n crm get deploy crm-api -o jsonpath='{.spec.template.spec.containers[0].image}'
docker image inspect crm-api:dev --format '{{index .Config.Labels "org.opencontainers.image.revision"}}'
```

## Triggers

Roll back when any of these hold after a deploy:

- readiness fails, or the API returns non-200 through the ingress
- the rollout does not converge inside its timeout
- a credential is found in the repository, a log, or an image layer

No approval needed. Rolling back is always allowed; explaining it afterwards is
cheaper than deciding during an incident.

## Procedure

**1. Detect and announce.** Say it out loud before fixing it, so nobody else
starts a parallel change.

**2. Redeploy the previous revision.** Never rebuild. A rebuild produces a new
artifact, which is a new unknown at the worst possible moment.

```
kubectl -n crm rollout undo deploy/crm-api
kubectl -n crm rollout status deploy/crm-api --timeout=300s
```

To target a specific revision instead of simply the previous one:

```
kubectl -n crm rollout history deploy/crm-api
kubectl -n crm rollout undo deploy/crm-api --to-revision=N
```

**3. Wait for convergence before judging.** The first request after a rollback
can fail while the outgoing pod drains and Traefik rebuilds its router. Poll,
do not sample once.

**4. Verify.** Below.

**5. Record the outcome.** In the release notes. No tokens, no secrets.

## Verification

```
curl -s -o /dev/null -w "%{http_code}\n" -H "Host: crm-api.localtest.me" \
  http://localhost:8088/actuator/health/readiness

curl -s -X POST http://localhost:8088/api/auth/login \
  -H "Host: crm-api.localtest.me" -H "Content-Type: application/json" \
  -d '{"username":"admin1","password":"admin1"}'

curl -s -o /dev/null -w "%{http_code}\n" -H "Host: crm-api.localtest.me" \
  -H "Authorization: Bearer <accessToken>" http://localhost:8088/api/customers
```

Expect 200, 200 with a token, and 200. Every customer route is 401 without one,
which is worth checking too — a rollback that restores service by disabling
authentication is not a rollback.

Use `admin1`, not `agent1`: `agent1`'s password was changed during manual testing
and no longer matches the seeded value.

## Timebox

Target under **15 minutes** from detection to verified smoke. Measured here: the
undo and reconvergence took under 30 seconds, so nearly the whole budget is
detection and the decision, not the mechanism.

## Rehearsal evidence

Rehearsed 2026-08-25 against `k3d-lab42`, namespace `crm`.

Fault injected deliberately:

```
kubectl -n crm set image deploy/crm-api crm-api=crm-api:does-not-exist
```

Observed:

- the new pod went to `ImagePullBackOff`, `0/1`
- the previous pod stayed `Running`, `1/1`
- `rollout status` timed out with `1 old replicas are pending termination`
- **the ingress kept returning 200 for the whole outage** — the rolling update
  never took the old pod down, so no request ever hit the broken one

Recovery:

```
kubectl -n crm rollout undo deploy/crm-api
```

- reported `rolled back`, then `successfully rolled out`
- the broken pod moved to `Terminating`, the good pod stayed up
- the image returned to `crm-api:dev`
- smoke after rollback: readiness 200, login 200

## What this rehearsal proves, and what it does not

It proves the mechanism and one genuinely useful property: **a bad image is
survivable**. Kubernetes would not terminate a healthy pod for an unhealthy
replacement, so the failure was contained to the rollout and never reached a
user. That is the demo worth showing.

It does **not** prove a version-to-version rollback. The two revisions here are
one working image and one that does not exist, so nothing was ever served by the
"bad" version. Proving that needs two revisions carrying two different *working*
images, which needs the pipeline to build and push an image per commit — the
`package` job currently produces a jar and a checksum, not an image.

That gap is worth naming in the defence rather than hoping nobody asks.
