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
| 7 | Nobody can tell which build is running | Digest and commit recorded before deploy; `k8s/deployment.yaml` pins the published GHCR digest (ADR-005 amendment); OCI revision label carries the commit | Rolling forward is a deliberate manifest commit — the cost the pin was chosen for |
| 8 | A bad deploy takes the service down | Rolling update keeps the healthy pod; rehearsed rollback | Proven for a missing image, not for a bad working version |
| 9 | An unauthenticated caller reads customer data | Deny by default; 401 asserted in unit tests and in `k8s/smoke.sh` | — |
| 10 | A token appears in a log or screenshot | Correlation IDs logged, not tokens; runbook says redact | Manual discipline |
| 11 | The smoke test runs against a real cluster | `OWN_CLUSTER=0` mode refuses to run without an explicit namespace, ingress and host header | The default mode still trusts the current kubectl context — fine while that context is disposable k3d |
| 12 | A cross-origin site drives the API in a user's browser | **Same-origin by construction.** The UI calls `/api` relatively and is always served from an origin that proxies `/api` to the backend server-side, so the browser only ever makes same-origin requests and no CORS headers are emitted. A page on any other origin gets no `Access-Control-Allow-Origin`, so the browser blocks it reading a response — deliberately | No allowlist to maintain and nothing to widen by mistake. The cost is that pointing the UI directly at the API's absolute origin would need a `CorsConfigurationSource` bean added first — see below |

## The CORS stance, in full

The application makes **no CORS configuration**, and that is the finished state, not an unfinished one. `SecurityConfig` keeps `http.cors(Customizer.withDefaults())` with no `CorsConfigurationSource` bean, so Spring emits no `Access-Control-*` headers at all. The frontend calls the API with a relative path (`src/api/v1/http.ts`, `VITE_API_BASE_URL` empty), so every request goes to the origin that served the page — and in every environment that origin forwards `/api` to the backend **server-side**, never in the browser:

| Environment | `/api` forwarded by | Browser's origin |
| ----------- | ------------------- | ---------------- |
| `npm run dev` | Vite dev proxy (`vite.config.ts`) | `localhost:5173` |
| Vercel (deployed UI) | `vercel.json` edge rewrite (`frontend/vercel.json`) | the `*.vercel.app` domain |
| One-host k8s (alternative) | Traefik routing `/api` → `crm-api` on one ingress host | the ingress host |

So the request the browser makes is always same-origin; the hop to the API is a server-side proxy the browser never sees. The security value is that there is nothing to get wrong: no origin allowlist that could be widened to `*` by a well-meaning edit, no preflight surface. A cross-origin caller is refused by the browser's own same-origin policy, which is the strongest possible answer because it needs no server cooperation.

The Vercel edge rewrite also avoids a mixed-content trap: the API is plain HTTP, so a browser on an HTTPS Vercel page cannot call it directly — but the rewrite runs at Vercel's edge (HTTPS to the browser, HTTP to the backend behind it), so the browser never makes the blocked request.

This stance holds only while `/api` is proxied. If a deployment instead sets `VITE_API_BASE_URL` to the API's absolute origin — making the browser call the backend host directly — those requests become cross-origin (and, HTTP-from-HTTPS, mixed content) and will fail until a `CorsConfigurationSource` bean names the exact allowed origins and a preflight test guards it. That is a deliberate, reviewable change, not something to leave to a default. `frontend/README.md` says this at the point where someone would be tempted to set the variable.

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
| Whether to guard `k8s/smoke.sh` on the kubectl context | Luke | `OWN_CLUSTER=0` guards the shared-cluster path; whether the default mode should also refuse unfamiliar contexts is still open |
