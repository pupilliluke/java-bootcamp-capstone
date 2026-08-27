# Week 6 requirements versus current state

Checked 2026-08-27 against `origin/develop` plus the planning docs landing with
this change. Source: course `labs/Week 6 - Capstone Project/`.

Lab 48 sets the gate everything else hangs off: *no Lab 49–52 work counts as
in-scope unless it maps to a backlog item, an ADR, and a measurable NFR.*

## Status

| Lab | Theme | Have | Missing or divergent |
| --- | ----- | ---- | -------------------- |
| 48 | Planning | C4 context + container, measurable NFRs, ADR-001–010, backlog, risk register, threat model, team plan, plan checklist | Re-check diagrams against code before the defense |
| 49 | Backend + messaging | interaction slice with Flyway + JPA, versioned event, consumer with version check + dedupe + DLT, messaging ITs, fixtures | `/v1/` prefix (#83) · 201 on create (#84) · enumerated channel with a 400 path (#82) · `backend-demo.md` (#85) · event carries notes, row has no actor (#86) · dedupe store is in-memory |
| 50 | Frontend + persistence | React journey, typed client, correlation header on every call, component tests, Playwright e2e with read-back | `data-api` checklist and `frontend-persistence-demo.md` · no `correlation_id` (or actor) column on the interaction row, so the guide's durability SELECT by correlation id cannot run (#88, actor in #86) · restart-durability proof not recorded (#88) |
| 51 | Security + delivery | deny-by-default JWT/RBAC with negative tests, six-job CI, Trivy gating at HIGH/CRITICAL with a triaged ignore file, non-root digest-pinned image, GHCR publish, three probes, smoke that breaks and recovers, rollback runbook | instructor ruling on k3d vs shared k3s, then one `OWN_CLUSTER=0` run in our namespace (#89) · rollback rehearsal between two working versions (#90) · the frontend has no served home outside `npm run dev`, and the CORS stance that choice implies is unwritten (#93) · CI and frontend on Node 20, stack guide says 22 |
| 52 | Defense | `evidence-index.md`, `ai-assistance.md` | presentation + PDF, timed demo script with a failure beat, ≥10 Q&A cards, feedback log, retrospective, self-assessment, four individual reflections · `reports/` is still empty |

## Divergences needing a decision

**Repo location.** Every guide places the tree at
`~/java-bootcamp/examples/customer-management-platform/`. We use a separate
repo. Deliberate, but confirm with the instructor — a reviewer looking where the
guide says will find nothing.

**k3d vs k3s.** Lab 51 lists it as a failure experiment: *"k3d / `:8088` as
capstone → Lab 51 is k3s; Service port 8080."* Our proven deploys are k3d (which
runs k3s inside Docker), and `k8s/smoke.sh` has an `OWN_CLUSTER=0` mode built
for the shared cluster that has not been exercised there yet. Needs an
instructor ruling, not our own reading. Tracked as #89.

**API version.** CAP-12 is `POST /api/v1/interactions` throughout Labs 48/49/51.
We expose `/api/interactions`. Now tracked as #83 — still cheap, and it stops
being cheap the day the demo script quotes URLs.

Closed since the last check: **digest pinning** (the `publish` job pushes every
develop/main build to GHCR and `k8s/deployment.yaml` pins that digest — PR #76,
ADR-005 amendment) and the **Lab 48 planning layer** (ADR-001–010, NFRs, C4
docs, team plan).

## Reading

Delivery is **ahead** of the brief: Lab 51 asks for a `verify` job and an
`image` job; we have six jobs plus a publish job, a real cluster that deploys,
breaks and recovers with twelve assertions (`docs/k8s-ci-evidence.txt`), and a
digest-pinned manifest.

What remains is **contract polish and the defense packet**. The interaction
slice works but diverges from the course contract in small, quotable ways
(#82–#86) — cheapest to fix before the demo script freezes them. Lab 52 is the
only lab still effectively unstarted, and it is the reconciliation mechanism
for everything else: the packet under `defense/` is where the delivery work
gets counted.
