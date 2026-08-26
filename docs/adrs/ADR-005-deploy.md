# ADR-005: Deploy to k3s with the image digest recorded, not pinned

- **Status:** Accepted
- **Date:** 2026-08-25
- **Deciders:** Luke
- **Related backlog:** CAP-12

## Context

We need one deploy target that runs the same way on a laptop and in CI, plus a
way back to a known-good version when a deploy goes bad. Nothing is pushed to a
registry yet, so the manifest cannot name a digest anyone else could pull. k3d
also imports into containerd, which computes its own image ID, so the digest
Docker reports and the one the pod reports are different strings for the same
bits.

## Decision

We will deploy to k3s with Deployment, Service, Ingress, ConfigMap and Secret.
The Deployment names a tag. The digest and commit of the tested build are
written into `docs/rollback-runbook.md` before the deploy, not after something
breaks. Rollback is `kubectl rollout undo`.

k3s runs as k3d locally and in CI, both pinned to `rancher/k3s:v1.35.5-k3s1`.

## Alternatives considered

| Option | Pros | Cons | Why not |
| ------ | ---- | ---- | ------- |
| A — Pin `@sha256:` in the manifest | Names the exact bits | Needs a registry; k3d reports a different ID | Nothing is pushed yet |
| B — Docker Compose only | Simpler | No probes, rollout or rollback | Misses Lab 51 |
| C — Managed cluster (AKS) | Closest to real | Cost and setup time | Outside the timebox |

## Consequences

- **Positive:** Probes, rolling update and rollback all work. CI applies these
  manifests, breaks the deploy on purpose and proves it recovers.
- **Negative / follow-ups:** The tag is mutable, so the manifest alone does not
  say which build is running. Lab 51 asks for a digest pin; closing that needs
  an image pushed per commit, which would also let us prove a
  version-to-version rollback instead of only that a bad image is survivable.
  *Update 2026-08-26:* the registry half is closed — the `publish` job in
  `ci.yml` pushes every develop/main build to GHCR (the course's named
  registry) tagged by commit SHA, and prints the repo digest in its run
  summary. The pin itself lands as a one-line manifest change after the first
  publish; the procedure is written above the `image:` line in
  `k8s/deployment.yaml`.
- **NFR impact:** Recovery. Rollback rehearsed, readiness back within seconds,
  ingress served 200 throughout the bad rollout.
- **Evidence later labs will need:** `docs/rollback-runbook.md`,
  `docs/k8s-ci-evidence.txt`, the `cluster` job in `.github/workflows/ci.yml`.

## Links

- Context/container: `docs/architecture/`
- Backlog stories: `docs/backlog.md`
