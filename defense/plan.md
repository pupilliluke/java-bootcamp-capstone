# Defense packet plan

Lab 52 wants seven artifacts. Two exist. This is the order to build the rest and
what each one needs.

Worth 15 rubric points, and the only category writing more code cannot help.

## State

| Artifact | State |
| -------- | ----- |
| `evidence-index.md` | Done — rubric-mapped, claim IDs, real links |
| `ai-assistance.md` | Done |
| `slide-outline.md` | Missing |
| `demo-script.md` | Missing |
| `technical-q-and-a.md` | Missing |
| `retrospective.md` | Missing |
| `self-assessment.md` | Missing |
| `final-presentation.pdf` | Missing |

Lab 52 hard gate: Labs 48 to 51 paths listed with **gaps labelled, not invented**.
`docs/rubric-check.md` and `docs/week6-gap-analysis.md` already do that, so the
gate is met before we start.

## Order

Demo script first. Everything else refers to it — the slides follow its beats,
the Q&A answers what it provokes, and the self-assessment scores what it shows.

1. `demo-script.md`
2. `technical-q-and-a.md`
3. `slide-outline.md`
4. `self-assessment.md`
5. `retrospective.md`
6. `final-presentation.pdf`

## 1. Demo script

Timed, deterministic, with a fallback for every step. Lab 52 requires a
**deny/fallback beat** — a moment where something is refused and a moment where
something is recovered.

Proposed beats, all of which already work:

| # | Beat | Proves | Fallback |
| - | ---- | ------ | -------- |
| 1 | Log in as `admin1` | JWT issued | screenshot |
| 2 | Anonymous `GET /api/customers` returns 401 | deny by default | `SecurityRulesTest` output |
| 3 | Create a customer, reload, it is still there | UI to PostgreSQL | psql row |
| 4 | Record an interaction on `CUS-1001` | the CAP-12 slice | `InteractionControllerTest` |
| 5 | Show the correlation ID in the logs | traceability | log excerpt |
| 6 | `kubectl set image` to an image that does not exist | ingress keeps serving 200 | `docs/k8s-ci-evidence.txt` |
| 7 | `kubectl rollout undo`, re-check readiness and 401 | recovery | runbook |

Beats 6 and 7 are `k8s/smoke.sh` steps 5 and 6, so they are already rehearsed and
timed at 164 seconds end to end.

Record the actual clock time of each beat. A script with no timings is not a
timed script.

## 2. Technical Q and A

Ten cards, each a question, a two-sentence answer, and a link. Draw from
decisions already documented rather than inventing new ones.

Suggested set: why PostgreSQL; why Kafka and what happens when the consumer is
down; why the readiness group includes `db` but liveness does not; why a tag and
not a digest; how a bad deploy is survived; what the 3 second probe timeout is
derived from; how a secret reaches the pod; what the SAST gates catch and what
they do not; what is not proven; what we would do next.

The last two matter most. Reviewers assess understanding, and a team that names
its own limits reads as more credible than one that does not.

## 3. Slide outline

Business problem, architecture, the demo, evidence, limits, next. One slide per
beat above is roughly right. Structure only — the PDF comes last.

## 4. Self-assessment

Score against the 100 point rubric, section 8.1. `docs/rubric-check.md` is the
input; every claim links to a file. Do not score a category higher than its
evidence.

## 5. Retrospective

Blameless, with owned actions. Real material from this build:

- Three checks were written that reported success without checking anything
  (kubeconform on an empty directory, `tee` swallowing an exit code, an image
  import that imported nothing). All three were found by breaking them on
  purpose.
- Two Maven runs on one `target/` directory produced failures that looked like
  broken tests and were not.
- A V2 migration collision between two branches with zero git conflicts.

## 6. Presentation PDF

Last. Nothing to decide once the outline and script are settled.

## Side task, optional: push to GHCR

Not about going live. A registry stores images; it does not run them. This is
about evidence quality, and it buys three things:

- `k8s/deployment.yaml` could pin `image: ghcr.io/...@sha256:...` for real, which
  is the open item in ADR-005
- Two working images means a genuine version-to-version rollback can be proven,
  not only that a bad image is survivable
- The k3d demo becomes "this is exactly how it goes to AKS, the only change is
  the cluster"

Half a day, free for a public repo, `GITHUB_TOKEN` already authenticates in
Actions. It strengthens three documents that already exist.

Do it after the packet, not instead of it.
