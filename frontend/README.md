# CRM frontend

React + TypeScript (Vite). In development it runs behind `npm run dev` with a
dev proxy; in production the `frontend/` folder deploys **standalone to Vercel**
(or any static host with edge rewrites) and talks to the deployed API. The
production home is **<https://www.neuralcrm.xyz>** — the apex redirects there
permanently (`vercel.json`), so `www` is the one canonical origin.

## The API connection, and why there is no CORS

Every API call uses a **relative path** — `src/api/http.ts` starts from
`VITE_API_BASE_URL || ''`, which is empty, so the app requests `/api/...` on
whatever origin served it. That single choice is what keeps the whole stack
**same-origin** in all three environments, so the backend needs no CORS config:

| Environment | How `/api` reaches the backend | Origin the browser sees |
| ----------- | ------------------------------ | ----------------------- |
| `npm run dev` | Vite dev proxy (`vite.config.ts`) forwards `/api` server-side | `localhost:5173` |
| Vercel | `vercel.json` rewrite forwards `/api` at the edge, server-side | `https://www.neuralcrm.xyz` (previews: `*.vercel.app`) |
| One-host k8s (alt.) | Traefik routes `/api` to `crm-api` behind one ingress host | the ingress host |

In every case the browser only ever calls the origin it was served from; the
proxy hop to the API happens server-side. So there is no cross-origin request,
no preflight, and deliberately no `CorsConfigurationSource` bean. Full stance in
`docs/threat-model.md`.

Do **not** set `VITE_API_BASE_URL` to the API's absolute URL to "just point it
at the cluster." The API is plain HTTP; a browser on an HTTPS Vercel page will
block that request as mixed content, and it would be cross-origin on top. The
edge rewrite in `vercel.json` avoids both — it is the supported path.

## Deploy to Vercel

1. **Import the repo** in Vercel and set **Root Directory = `frontend`**. Vercel
   detects Vite; build command `npm run build`, output `dist` — leave the
   defaults.
2. **Point the rewrite at your API.** `vercel.json` proxies `/api` and
   `/actuator` to `http://crm-student02.100.22.136.97.nip.io`. That is
   **student02's** namespace host — if you deploy your own, change both
   `destination` hosts to `crm-studentNN.100.22.136.97.nip.io`. (Vercel does not
   interpolate env vars into rewrite destinations, so this host is edited in the
   file, matching the per-student pattern in `k8s/cluster/configmap.yaml`.)
3. **Leave Google Sign-In off** — it is off by default now (see below), so no env
   var is needed for password-only login. The Google button and its script are
   not injected unless `VITE_ENABLE_GSI=true`.
4. **Attach the domain.** Project → Settings → Domains: add
   `www.neuralcrm.xyz` (primary) and `neuralcrm.xyz`. At the registrar, point
   `www` at `cname.vercel-dns.com` (CNAME) and the apex at Vercel's A record
   `76.76.21.21`. The apex needs no redirect config in the dashboard —
   `vercel.json` already 308s it to `www`, so the redirect is versioned with
   the code instead of living in a console.
5. Deploy. Sign in with the seeded demo accounts (`agent1`/`agent1`,
   `admin1`/`admin1`).

### Google Sign-In

Google Sign-In is **off by default** (opt-in). No button and no
`accounts.google.com` script are injected unless `VITE_ENABLE_GSI=true`. The
default is deliberate: Google refuses any serving origin not listed in the OAuth
client's **Authorized JavaScript origins**, showing a full-page **"Error 400:
origin_mismatch"** instead of a login — so a deploy to a new domain would
otherwise ship a broken button.

To actually enable Google login on a deployed origin — two steps, and the first
is not in this repo:

1. In **Google Cloud project `398192114075`** → APIs & Services → Credentials →
   the OAuth 2.0 client `398192114075-fb8j…`, add the exact serving origins to
   **Authorized JavaScript origins**: `https://www.neuralcrm.xyz`,
   `https://neuralcrm.xyz`, and each `https://<deployment>.vercel.app` preview it
   should work on (scheme + host only — no path, no wildcards). Only the project
   owner can do this.
2. Set `VITE_ENABLE_GSI=true` for that build — a Vercel project env var, or
   `.env.local` for local dev at `http://localhost:5173` (which must also be a
   registered origin).

CI and the Playwright journey leave it off, which is why the customer-journey
test never loads the third-party script.

### The one dependency to check first

The Vercel edge fetches the API from Vercel's own servers, not your laptop. That
only works if the course box accepts connections from arbitrary external IPs on
`:80`. The setup notes say Postgres and Kafka are restricted to class IPs and
are silent on the ingress; a quick check is to open
`http://crm-studentNN.100.22.136.97.nip.io/actuator/health/readiness` from a
phone on cellular. If it answers, Vercel can reach it. If the box is class-IP
restricted, Vercel cannot proxy to it — fall back to running the UI on a laptop
with `npm run dev` (the dev proxy reaches the cluster from your allowed IP), or
serve the UI same-origin inside the cluster.

## Local development

```
npm ci
npm run dev        # http://localhost:5173, proxied to a local backend on :8080
```

Point the dev proxy at the deployed API instead by setting `VITE_PROXY_TARGET`
in a gitignored `.env.local` — see the repo README's course-cluster section.

## Scripts

| Command | What it does |
| ------- | ------------ |
| `npm run dev` | Vite dev server with the `/api` proxy |
| `npm run build` | Type-check (`tsc -b`) then build to `dist/` |
| `npm run test:ci` | Vitest component and API unit tests |
| `npm run test:e2e` | Playwright browser journey (starts backend + Vite) |
