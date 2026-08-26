# Week 6 requirements versus current state

Checked 2026-08-25 against `origin/develop`. Source: course `labs/Week 6 - Capstone Project/`.

Lab 48 sets the gate everything else hangs off: *no Lab 49–52 work counts as
in-scope unless it maps to a backlog item, an ADR, and a measurable NFR.*

## Status

| Lab | Theme | Have | Missing or divergent |
| --- | ----- | ---- | -------------------- |
| 48 | Planning | backlog, risk register | **ADRs (none), NFRs, C4 context + container, team plan, plan checklist** |
| 49 | Backend + messaging | interaction slice, migration, Kafka + DLT, tests, fixtures | `/v1/` prefix, event contract naming, `backend-demo.md` |
| 50 | Frontend + persistence | React journey, typed client, JPA, UI tests | two checklists, timeline unverified |
| 51 | Security + delivery | CI, SAST gates, Dockerfile, probes, 401/403 tests, rollback runbook | digest pin, k3s, checklist + demo + threat model |
| 52 | Defense | — | all 7 artifacts (`defense/` holds only `ai-assistance.md`) |

## Four divergences needing a decision

**Repo location.** Every guide places the tree at
`~/java-bootcamp/examples/customer-management-platform/`. We use a separate
repo. Deliberate, but confirm with the instructor — a reviewer looking where the
guide says will find nothing.

**k3d vs k3s.** Lab 51 lists it as a failure experiment: *"k3d / `:8088` as
capstone → Lab 51 is k3s; Service port 8080."* All our Kubernetes work is k3d at
`:8088`. k3d does run k3s inside Docker, so there is a real argument — but this
needs an instructor ruling, not our own reading.

**API version.** CAP-12 is `POST /api/v1/interactions` throughout Labs 48/49/51.
We expose `/api/interactions`. Cheap now, expensive once the demo script and
frontend are wired to it.

**Digest pinning.** Lab 51 Step 7 wants `image: ...@sha256:...` in the
deployment. We pin a tag, defended in `rollback-runbook.md` with Lab 41's
capture-and-record model. That was right for Labs 41–42; Lab 51 changes the
requirement. Closing it needs an image pushed per commit — the same gap that
limits the rehearsal to "a bad image is survivable."

## Reading

Delivery is **ahead** of the brief: Lab 51 asks for a `verify` job and an
`image` job; we have six jobs including a real cluster that deploys, breaks and
recovers with twelve assertions (`docs/k8s-ci-evidence.txt`).

Documentation is **behind**. The Lab 48 planning layer is the highest-value
gap — the gate above means the delivery work only counts once it exists, and
most of its content is already written as comments in `backend/Dockerfile`,
`k8s/deployment.yaml` and `docs/rollback-runbook.md`. Transcription, not new
analysis.
