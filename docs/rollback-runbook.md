# Rollback runbook

Ported from the lab 44 runbook and re-grounded in this repository. Every command
below was run against the real cluster, and the observed output is recorded at
the bottom rather than described from memory.

## Known-good identity

Recorded **before** a deploy, not looked up during an incident. Capture happens
on every build; a digest written down after something breaks is a digest for the
version that broke.

| Field | Value |
| ----- | ----- |
| Cluster | `k3d-crm-local`, namespace `crm` |
| Deployment | `crm-api` |
| Tag deployed | `crm-api:dev` |
| **Digest** | `sha256:ebfe1486a16d77a6dedc0c5f8ed92eec56618f55fd1cdf53d789667d016e69b2` |
| Commit | `5f1bb6d8013f204db7fd5965d6604352ba5fd889` (`org.opencontainers.image.revision`) |
| Ingress | `crm-api.localtest.me` via Traefik on `:8088` |
| Verification | readiness, then login, then an authenticated `GET /api/customers` |

The digest is the row that earns this table. Rolling back to "the previous tag"
is not a thing that exists — the tag moved, and what it used to point at may be
untagged or gone. Rolling back to a recorded digest is one command.

A digest with no commit beside it is only half an answer: it says what ran, not
what it was built from. Capture both together:

```
docker image inspect crm-api:dev --format '{{.Id}}'
docker image inspect crm-api:dev --format '{{index .Config.Labels "org.opencontainers.image.revision"}}'
```

`.Id` rather than `.RepoDigests` because nothing is pushed to a registry yet. Once
images are pushed, `{{index .RepoDigests 0}}` is the value to record and the
Deployment can pin `crm-api@sha256:...` directly instead of a tag.

One thing to know before trusting a digest against this cluster: `k3d image
import` loads into containerd, which computes its own image ID. Docker reports
`sha256:ebfe1486...` for the build the pod is running, while the pod itself
reports `sha256:628d95bd...`. They refer to the same bits; they are not the same
string, so compare like with like.

Tag scheme, three tags on one build:

| Tag | Purpose |
| --- | --- |
| `crm-api:dev` | the name a human types |
| `crm-api:0.0.1-SNAPSHOT` | matches the Maven version in `pom.xml` |
| `crm-api:<git-sha>` | ties the image to the commit that produced it |

## What is checked automatically

Two CI jobs cover the manifests, and they answer different questions.

`manifests` runs `kubeconform -strict`, pinned to the Kubernetes version the
cluster runs. That catches a misspelled field or a retired `apiVersion` before
anyone applies anything. It was checked against deliberate breakage rather than
assumed: renaming `failureThreshold` to `failureThresold` and downgrading the
Ingress to `networking.k8s.io/v1beta1` both fail the job. It also carries a
file-count guard, because kubeconform exits 0 on an empty directory -- "0
resources found ... Errors: 0" -- and would otherwise go green having validated
nothing at all.

`cluster` answers the question a schema check cannot: does the thing these files
describe actually work. It builds the image, creates a real k3d cluster, applies
these manifests and runs `k8s/smoke.sh`, which asserts twelve things -- that the
deployment converges, that readiness returns 200 through the ingress, that login
issues a token, that an authenticated read is 200 and an anonymous one is 401;
then deliberately deploys an image that does not exist and asserts the rollout
does *not* converge while the ingress keeps serving; then rolls back and
re-checks all of it.

### Reproducing the cluster

The Kubernetes version has to be pinned when the cluster is created, or the two
jobs disagree about what they are checking:

```
k3d cluster create crm-local --agents 0 \
  -p "8088:80@loadbalancer" \
  --api-port 127.0.0.1:6445 \
  --image rancher/k3s:v1.35.5-k3s1
```

Neither flag is optional.

`--image` matches what k3d v5.9.0 installs by default today, but that default
moves with every k3d release, and a cluster created on a different one is not the
cluster the schema check describes. The `cluster` job pins the same image.

`--api-port` pins the API server to loopback. Without it k3d writes
`https://host.docker.internal:<port>` into the kubeconfig, which resolves to the
machine's LAN address. On a laptop that has moved networks that address does not
answer, and every kubectl command fails with a dial timeout against a cluster
that is running perfectly well. Observed here: `dial tcp 172.20.10.4:58193`.

One mismatch this does not solve. Docker Desktop currently ships kubectl v1.30.5,
and a 1.35 server is outside the supported +/-1 skew, so kubectl prints a warning
on every command. The full smoke test passes regardless, but the configuration is
not a supported one. Closing it means putting a newer kubectl ahead of Docker's
on PATH.

### Evidence that it can fail

A check that has only ever passed is not evidence. Each edit below was applied to
`k8s/deployment.yaml`, run against a real cluster, and reverted:

| Edit | kubeconform | `cluster` |
| ---- | ----------- | --------- |
| Startup probe path `readiness` -> `raediness` | passes | caught, exit 1 |
| `image: crm-api:dev` -> a tag that does not exist | passes | caught, exit 1 |
| `secretRef` for `crm-api-secrets` deleted | passes | caught, exit 1 |

The verbatim output of all four runs is in `docs/k8s-ci-evidence.txt`.

The middle column is the argument for the second job existing. All three are
legal Kubernetes; schema validation has no opinion about whether a path resolves,
an image exists, or a Secret is wired.

Running them found a real defect rather than confirming a hope. The second row
passed at first, because `smoke.sh` ran `kubectl set image` unconditionally and
overwrote the typo'd tag with a known-good one. The image now comes from the
manifest unless `IMAGE` is set explicitly, so the committed tag is under test.

Two limits worth knowing. All three mutations fail at the same point with the
same message, `timed out waiting for the condition` -- the job tells you the
deployment broke, not which edit broke it, and the `if: failure()` diagnostics
step is where the answer actually lives. And these are three hand-picked edits
chosen to match three known failure modes, not systematic mutation coverage:
they prove the check is failable, not that it catches everything. A wrong CPU
limit or a resource request too large to schedule would still get through.

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

The same three checks, plus the break-and-recover rehearsal, are automated in
`k8s/smoke.sh` -- which is what the `cluster` job runs. Doing them by hand is
still worth it during a real incident, when the question is what is happening
rather than whether the manifests are sound:

```
./k8s/smoke.sh
```

## Timebox

Target under **15 minutes** from detection to verified smoke. Measured here: the
undo and reconvergence took under 30 seconds, so nearly the whole budget is
detection and the decision, not the mechanism.

## Rehearsal evidence

Rehearsed 2026-08-25 against `k3d-crm-local`, namespace `crm`.

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
