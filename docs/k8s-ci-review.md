# Kubernetes CI review

Branch: k8s-manifests
Date: 2026-08-25
Scope: `k8s/`, `docs/rollback-runbook.md` and `.github/workflows/ci.yml`

The branch adds Kubernetes manifests for the backend, a rollback runbook, and
two CI jobs. The `manifests` job validates `k8s/*.yaml` with kubeconform v0.8.0
under `-strict`, pinned to Kubernetes 1.28.15. The `cluster` job builds the
image, creates a real k3d cluster, applies the manifests and runs
`k8s/smoke.sh`, which deploys, smoke tests, breaks the deployment on purpose
with an image that does not exist, and asserts recovery through
`kubectl rollout undo`.

## Version numbers in this document

The findings below were raised against the branch as it stood, when both the
schema check and the cluster were pinned to Kubernetes 1.28.15. After the review,
all three pins were bumped to 1.35.5: the `kubeconform -kubernetes-version` flag,
the `--image rancher/k3s:` argument in the `cluster` job, and the create command
in `docs/rollback-runbook.md`. Local kubectl was moved to v1.35.5 to match.

The 1.28.15 references are kept as the record of what was found. Current state is
1.35.5 everywhere, re-verified: schema validation passes and still fails on a
broken manifest, twelve assertions pass in 164 seconds, and all three mutations
are caught. See `docs/k8s-ci-evidence.txt`.

## Method

Five reviewers read the branch independently, each under a different lens. Every
finding raised was handed to a separate verifier whose task was to refute it. 20
findings were raised, 17 were refuted, 3 were confirmed. All three confirmed
findings were in `.github/workflows/ci.yml`. None were in the manifests, the
smoke test or the runbook.

## Context: the smoke test could not catch a bad image tag

Earlier in the same session, mutation testing of `k8s/smoke.sh` found that the
script ran `kubectl set image` unconditionally with `IMAGE` defaulting to
`crm-api:dev`. A wrong image tag committed in `k8s/deployment.yaml` was
overwritten with a known-good one before the deployment was tested, so the smoke
test could never catch a bad image tag. `IMAGE` now defaults to empty and the
manifest's own tag is used unless `IMAGE` is set explicitly (`k8s/smoke.sh:25`
and `k8s/smoke.sh:110`). The verbatim mutation evidence is in
`docs/k8s-ci-evidence.txt`.

That defect was found by mutating the thing under test rather than by reading
it, and prompted the review below.

## Finding 1: the schema gate was vacuous (high)

The validation step ran:

```
./kubeconform -strict -summary -verbose -kubernetes-version 1.28.15 k8s/ | tee kubeconform.txt
```

The pipe into `tee` was added so the report could be uploaded as a CI artifact.
GitHub Actions runs `run:` blocks under `bash -e {0}` by default, which does not
include `-o pipefail`; only an explicit `shell: bash` adds it. The step's exit
status was therefore tee's, which is always 0, and kubeconform's exit 1 was
discarded.

The committed version at 13d85b9 ran kubeconform unpiped and gated correctly.
The uncommitted edit that added the artifact removed the gate.

The step's other check, `grep -q "in $expected file" kubeconform.txt`, does not
compensate. kubeconform prints the same file-count phrase whether the resources
are valid or invalid.

Verified against kubeconform v0.8.0 and a real broken manifest, with
`failureThreshold` renamed to `failureThresold` in `k8s/deployment.yaml`:

| Invocation | Exit |
| ---------- | ---- |
| kubeconform alone | 1 |
| the step as CI would run it (`bash -e`) | 0 |
| the same step with `set -o pipefail` added | 1 |

kubeconform alone reported `Summary: 8 resources found in 7 files - Valid: 7,
Invalid: 1, Errors: 0, Skipped: 0` and exited 1. Through the pipe under `bash
-e`, the broken manifest passed.

Fix: `set -o pipefail` as the first line of the run block
(`.github/workflows/ci.yml:304`).

After the fix the step was re-extracted from `ci.yml` programmatically and
re-tested both ways:

| Manifest | Summary | Exit |
| -------- | ------- | ---- |
| good | `Valid: 8, Invalid: 0, Errors: 0, Skipped: 0` | 0 |
| broken | `Valid: 7, Invalid: 1, Errors: 0, Skipped: 0` | 1 |

Both runs reported `8 resources found in 7 files`.

What it prevented: the `manifests` job would have reported success on any
schema-invalid manifest. The job comment claimed the check had been verified to
fail on a `failureThresold` typo. That claim was true of the committed version
and had stopped being true. The branch carries two gates, schema validation and
the cluster smoke test, and this one had silently stopped being one.

## Finding 2: the cluster and the schema check disagreed about the version (medium)

`AbsaOSS/k3d-action@v2.4.0` was used with no `k3d-version` and no `--image`.
That action defaults to k3d v5.4.6, released in 2022, whose baked-in k3s is
approximately 1.24. The `manifests` job validates against 1.28.15, and the local
cluster used for the rehearsal in `docs/rollback-runbook.md` runs v1.28.15+k3s1,
confirmed with `kubectl version`. The CI cluster was up to four minor versions
below both.

No manifest currently on the branch is affected. Every object uses `v1`,
`apps/v1` or `networking.k8s.io/v1`, and every field used has been stable across
1.21 to 1.28. The defect is that the documented guarantee was false and the gate
was looser than described.

Fix: an explicit `--image rancher/k3s:` argument added to the k3d args
(`.github/workflows/ci.yml:378`), and `docs/rollback-runbook.md` pins the same
image for the local cluster. Both now name v1.35.5-k3s1, matching the schema
check and the local cluster.

What it prevented: a manifest field introduced after 1.24 would pass the schema
check and then be rejected or silently dropped by the cluster, and the CI run
was not evidence about the same Kubernetes version as the recorded local
rehearsal.

## Finding 3: a job timeout would have destroyed the diagnostics (medium)

`timeout-minutes: 20` on the `cluster` job sat below the job's own worst-case
budget. `k8s/smoke.sh` allows 420s for the postgres rollout, 300s for the
crm-api rollout, 60s for the deliberate break and 300s for the rollback, on top
of a cold `docker build`.

Exceeding `timeout-minutes` cancels a job rather than failing it, and a
cancelled job skips steps guarded by `if: failure()`. The Diagnostics step,
which dumps pod state, descriptions, logs and events, was so guarded. The one
run where the cluster state mattered most would have captured nothing before the
runner was destroyed.

Fix: `timeout-minutes` raised to 30 (`.github/workflows/ci.yml:352`), and the
Diagnostics condition changed to `if: ${{ failure() || cancelled() }}`
(`.github/workflows/ci.yml:400`).

What it prevented: a red `cluster` job with no cluster left to inspect and no
logs explaining why.

## Refuted findings worth recording

17 findings were refuted. Two are recorded here because the mechanics were read
correctly and only the impact was wrong.

### Destructive writes against the current kubectl context

The claim: `k8s/smoke.sh` performs destructive writes (`kubectl apply`,
`kubectl patch`, `kubectl set image`) against whatever kubectl context is
current, with no guard that the target is a throwaway cluster.

Refuted on impact. No live or hosted cluster exists anywhere in the repository.
The only targets are the ephemeral CI k3d cluster and a laptop k3d cluster.
`.github/` contains no kubeconfig secret, no cloud login action and no deploy
job. This is a latent risk that becomes real if a hosted cluster is ever added,
not a current defect.

### The single ingress sample in step 5

The claim: the assertion that the ingress kept serving 200 throughout rests on a
single sample taken after the 60-second break window (`k8s/smoke.sh:165`).

Refuted. The replacement image does not exist, so the bad pod stays in
`ImagePullBackOff` indefinitely. The failure being guarded against, a healthy
pod torn down for an unhealthy replacement, is permanent rather than transient
and is still present at the sample point.

## Limits and follow-ups

The version pin in finding 2 changes nothing about the manifests as they stand.
Every field in `k8s/` is valid on both 1.24 and 1.28. The pin closes the gap for
fields added later.

The kubectl-context risk stays harmless only while the repository holds no
credentials for a cluster that is not disposable.

`docs/k8s-ci-evidence.txt` described the diagnostics step as `if: failure()`,
which matched `.github/workflows/ci.yml` when it was written and predated the
finding 3 fix. It has since been corrected to record the condition and the
reason for it.

15 of the 17 refuted findings are not recorded here.

The limit `docs/k8s-ci-evidence.txt` already records is unchanged by this
review: the smoke test proves a bad image is survivable, not a
version-to-version rollback, because one of the two revisions is an image that
never existed.
