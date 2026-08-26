# Threat model — delivery and deployment

Scope is the delivery path: the image, the pipeline, the manifests and the
cluster. The application's own threat surface (authentication, authorisation,
input validation) is owned by the backend and frontend work and is not covered
here.

## What is worth protecting

| Asset | Where it lives |
| ----- | -------------- |
| JWT signing key | Secret `crm-api-secrets`, and gitignored `.env` locally |
| Database password | Same |
| Azure database credentials | gitignored `.env` only |
| Customer records | PostgreSQL |
| The image | Local Docker daemon and the cluster's containerd |

The repository is public. Anything committed is published.

## Threats and what is done about them

| # | Threat | Mitigation | Residual |
| - | ------ | ---------- | -------- |
| 1 | A secret is committed | `.env` gitignored; `.dockerignore` excludes `.env` and `.env.*` from the build context; Trivy secret scan on every image | Nothing blocks a determined `git add -f` |
| 2 | A secret rides in an image layer | Nothing is baked at build time; every value arrives as an environment variable at run time; container-structure-test asserts no credential in the layers | — |
| 3 | The example Secret is applied by accident | Moved to `k8s/examples/`, out of reach of `kubectl apply -f k8s/` | Someone using `-R` would still pick it up |
| 4 | The container is compromised and runs as root | Non-root UID 10001, `allowPrivilegeEscalation: false`, all capabilities dropped | `readOnlyRootFilesystem` is false |
| 5 | The base image quietly changes under us | Both `FROM` lines pinned by digest | Bumping is a manual, deliberate commit |
| 6 | A known CVE ships | Trivy gates the `image` job on HIGH and CRITICAL, over both the OS layer and the fat jar's `BOOT-INF/lib` | Gated, but after a build rather than in the first minute. Runtime dependencies only; test and provided scope are not scanned |
| 7 | Nobody can tell which build is running | Digest and commit recorded before deploy; OCI revision label carries the commit | The manifest names a mutable tag. See ADR-005 |
| 8 | A bad deploy takes the service down | Rolling update keeps the healthy pod; rehearsed rollback | Proven for a missing image, not for a bad working version |
| 9 | An unauthenticated caller reads customer data | Deny by default; 401 asserted in unit tests and in `k8s/smoke.sh` | — |
| 10 | A token appears in a log or screenshot | Correlation IDs logged, not tokens; runbook says redact | Manual discipline |
| 11 | The smoke test runs against a real cluster | Every cluster today is disposable k3d | No guard on the kubectl context. Becomes real the day a hosted cluster exists |

## Decisions that follow from this

- Secrets are created from the command line, never from a file in the repository.
- `k8s/examples/secret.example.yaml` records key names only.
- CI uses no GitHub secrets. The test database is a throwaway container and the
  test suite supplies its own JWT secret, so there is nothing in the pipeline to
  leak.

## Open

| Item | Owner | Decision needed |
| ---- | ----- | --------------- |
| When to bump the base image digest | Luke | Eight Go defects in the base image's `/usr/bin/pebble` are silenced until 2026-11-26 in `.trivyignore.yaml`. A rebuilt Temurin digest is the fix |
| Whether to guard `k8s/smoke.sh` on the kubectl context | Luke | Only matters once a non-disposable cluster exists |
