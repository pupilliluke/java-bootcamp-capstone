# Security and deploy checklist

What is checked, where it runs, and whether it can fail. A gate that has only
ever passed is not a gate, so the last column says how each one was proven to
fail.

## Automated on every push and pull request

| # | Check | Where | Proven to fail by |
| - | ----- | ----- | ----------------- |
| 1 | Backend build and tests, real PostgreSQL service | `backend` job | Normal test failures |
| 2 | Frontend types, tests, build | `frontend` job | Normal test failures |
| 3 | Dockerfile lint | `image` job, hadolint | — |
| 4 | Image structure: non-root UID 10001, labels, no JDK or sources in the runtime layer | `image` job, container-structure-test | — |
| 5 | Image vulnerability and secret scan | `image` job, Trivy | — |
| 6 | Dependency CVEs | `image` job, Trivy over the fat jar | Caught CVE-2026-54291 in pgjdbc, which Dependency-Check scored 5.9 and passed. Fixed by pinning postgresql 42.7.13 |
| 7 | Manifests valid for the target Kubernetes version | `manifests` job, kubeconform `-strict` | Renaming `failureThreshold` to `failureThresold` exits 1 |
| 8 | Manifests directory is not empty | file-count guard | An empty directory fails the job |
| 9 | Deploy, serve, break, recover on a real cluster | `cluster` job, `k8s/smoke.sh` | Three manifest mutations, all caught |
| 10 | Customer journey through the browser | `cluster` job, Playwright | — |

## Manual, before a release

| # | Confirm | Notes |
| - | ------- | ----- |
| 1 | Digest and commit recorded before deploy | `docs/rollback-runbook.md` |
| 2 | Secret created from the command line, not from a file | Never `kubectl apply` a secret manifest |
| 3 | No `.env`, kubeconfig or token in the diff | `git status` before commit |
| 4 | Rollback rehearsed since the manifests last changed | `k8s/smoke.sh` |

## Access control

| Call | Expected |
| ---- | -------- |
| Customer read with no token | 401 |
| Customer read with a valid token | 200 |
| Admin route with AGENT role | 403 |
| Login with seeded `admin1` | 200 with a token |

Covered by `SecurityRulesTest`, `AppUserAuthTest`, `CustomerControllerTest`,
`AdminUserControllerTest`, and again end to end in `k8s/smoke.sh` steps 4 and 6.

## Known gaps

| Gap | Consequence |
| --- | ----------- |
| Trivy runs `ignore-unfixed`, and only over what the image ships | A HIGH with no available fix is invisible, as is anything in test or provided scope |
| Image is never pushed to a registry | The manifest pins a tag, not a digest. See ADR-005 |
| `k8s/smoke.sh` writes to whatever kubectl context is current | Harmless while every cluster is disposable |
