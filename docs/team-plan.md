# Team plan

Owners, backups, and the order the remaining work has to happen in. This
transcribes the roles from the check-in doc into the repository, where Lab 48
says they belong. It is a working agreement, not a org chart — correct it in
review if a row is wrong.

## Lanes

One owner and one backup per lane. The backup is not decoration: they review
that lane's pull requests and can walk its story at the defense if the owner is
mid-demo or stuck.

| Lane | Owner | Backup | Covers |
| ---- | ----- | ------ | ------ |
| Security and delivery | Luke Pupilli | Himank Juttiga | JWT and auth flow, `SecurityConfig`, CI/CD, Docker and Kubernetes, configuration and the setup script |
| Customers and data | Tim Mooney | Chase Bulkin | Customer API and service, JPA entities, migrations, Azure PostgreSQL |
| Messaging and docs | Chase Bulkin | Tim Mooney | Kafka producer, consumer, DLT; ADRs and the evidence index |
| Frontend | Himank Juttiga | Luke Pupilli | React journey, typed API client, component and e2e tests |
| Defense coordinator | **unassigned — fill at the next stand-up** | — | Demo script, rehearsal schedule, packet assembly under `defense/` |

The backup pairings follow the code: customers-and-data and messaging share the
service and entity layer, so Tim and Chase already read each other's changes.
Auth spans the stack — issued and verified in the backend, held and attached in
the frontend — so Luke and Himank cover each other's ends of it.

## Rotation before the defense

The rubric scores Q&A depth *across the team*, not per specialist. From now to
the defense: review at least one pull request outside your lane, and at
rehearsal each person walks one story that is not theirs.

## Working agreements

Written down because each one has already earned its line:

- Feature branch → pull request → `develop`. `main` advances only from
  `develop`; nobody pushes to `main` directly.
- Nothing merges unread. The reviewer is by default the lane's backup.
- Secrets live in the gitignored `.env` only. The Azure admin password moves by
  password manager, never through a chat channel.
- Evidence lands in the same pull request as the work — tick
  `docs/lab-coverage.md` in the PR that earns the tick.
- Synthetic data only, everywhere: fixtures, tests, screenshots, slides.

## Critical path

Order, not dates — dates live in the check-in doc. Each step exists because a
later one consumes it.

1. **API contract fixes, before the demo script freezes URLs and statuses:**
   `/api/v1` prefix and the compatibility rule (#83), 201 on create (#84),
   enumerated channel with the 400 failure path (#82). Cheap now; every one of
   them rewrites the demo script, smoke checks, and Q&A cards if it waits.
2. **Event contents decision (#86):** what the interaction event carries —
   notes and actor — has to be settled before the security story is written.
3. **Backend walkthrough (#85):** `docs/backend-demo.md`, the reproduce-it-cold
   companion to `docs/security-deploy-demo.md`.
4. **Course-cluster run:** `OWN_CLUSTER=0` smoke against our own namespace,
   the instructor ruling on k3d versus the shared k3s, and a rollback rehearsal
   between two working versions (the recorded one recovers from a missing
   image).
5. **Defense packet:** timed demo script with one injected failure and the
   fallback pack, at least ten Q&A cards, slides exported to
   `defense/final-presentation.pdf`.
6. **Retrospective, four individual reflections, and the self-assessment**,
   reconciled against the rubric through `defense/evidence-index.md`.
7. **Full rehearsal:** timed run, kill the live API once on purpose, and say
   the fallback line out loud.

Steps 1–3 can run in parallel across lanes; 5 depends on 1–4; 6 and 7 close.
