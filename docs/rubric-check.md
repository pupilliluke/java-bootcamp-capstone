# Rubric check

Checked 2026-08-26 against `origin/develop`. Rubric: section 8.1, 100 points.

## 1. Architecture and planning (15)

- [x] Prioritized backlog — `docs/backlog.md`
- [x] Full-stack architecture — `docs/architecture.md`, `docs/architecture/context.md`, `docs/architecture/container.md`
- [x] CI/CD plan — `.github/workflows/ci.yml`, 6 jobs
- [x] Risks and mitigations — `docs/risk-register.md`
- [x] ADRs — 7 files in `docs/adrs/`
- [x] Measurable NFRs — `docs/nfrs.md`
- [ ] Roles and ownership plan — no `docs/team-plan.md`
- [ ] Two files numbered ADR-005 — `ADR-005-database-test-strategy.md`, `ADR-005-deploy.md`

## 2. Backend and messaging (20)

- [x] Spring Boot services, layered — `api/` to `service/` to `repository/`
- [x] Kafka integration — `InteractionEventProducer`, `InteractionEventConsumer`
- [x] Versioned event contract — `InteractionEvent.version`, topic `crm.interaction.v1`
- [x] Idempotent consumer — `ProcessedEventStore.markIfNew(eventId)`
- [x] Dead letter topic — `KafkaConfig`, `FixedBackOff(1000, 2)`
- [x] Tests, happy and failure — 15 files, 81 unit and 9 integration
- [x] Review evidence — PRs #38 to #61
- [ ] `/api/v1/` prefix — routes are `/api/interactions`
- [ ] Durable dedupe store — `InMemoryProcessedEventStore` is in memory

## 3. Frontend and persistence (15)

- [x] React journey on the APIs — `frontend/src/pages/`
- [x] Typed API client — `frontend/src/api/`
- [x] PostgreSQL via JPA — `V1__app_user.sql`, `V2__interaction.sql`, `V3__customer.sql`
- [x] End-to-end flow — `frontend/e2e/customer-journey.spec.ts`, run in CI
- [x] Loading, error, empty states — 44 frontend tests
- [ ] Interactions written to Azure — Azure holds 3 customers and 0 interactions

## 4. Testing and quality (15)

- [x] Unit — 81 passing
- [x] Integration — `InteractionMessagingIT`, `AppUserRepositoryIT`, `CustomerRepositoryIT`
- [x] UI — 44 tests, 11 files
- [x] Smoke — `k8s/smoke.sh`, 12 assertions
- [x] Repeatable — every job runs on push and pull request
- [x] Documented limitations — `docs/k8s-ci-evidence.txt`, `defense/evidence-index.md`
- [x] Checks proven failable — 3 manifest mutations caught, kubeconform breakage verified

## 5. Security, CI/CD and deployment (20)

- [x] SAST evidence — hadolint, Trivy, Dependency-Check
- [x] Pipeline — `backend`, `frontend`, `image`, `manifests`, `cluster`, `package`
- [x] Container — multi-stage, non-root UID 10001, digest-pinned bases
- [x] Image structure tests — `backend/container-structure-test.yaml`
- [x] Deploy — `k8s/`, applied by the `cluster` job
- [x] Readiness — probes plus `readiness.include: readinessState,db`
- [x] Rollback — `docs/rollback-runbook.md`, `smoke.sh` step 6
- [x] JWT and RBAC negative tests — `SecurityRulesTest`, `AppUserAuthTest`
- [x] No secrets committed — `.env` gitignored, example Secret in `k8s/examples/`
- [ ] Image pinned by digest in the manifest — pins a tag, see ADR-005
- [ ] SAST is a gate — Dependency-Check reports, it does not fail the build
- [ ] k3s — everything runs on k3d, which Lab 51 names as a failure experiment

## 6. Final defense and professionalism (15)

- [x] Evidence index — `defense/evidence-index.md`
- [x] Design tradeoffs — 7 ADRs with alternatives and consequences
- [ ] Demo script
- [ ] Technical Q and A cards
- [ ] Retrospective
- [ ] Self-assessment
- [ ] Presentation

## Standing

| Category | Points | State |
| -------- | -----: | ----- |
| Architecture and planning | 15 | one file missing |
| Backend and messaging | 20 | two gaps |
| Frontend and persistence | 15 | one gap |
| Testing and quality | 15 | complete |
| Security, CI/CD, deployment | 20 | two gaps, one divergence |
| Final defense | 15 | five of seven missing |

Both 20-point categories are in good shape. The exposure is the defense packet,
worth 15 points, and it is the one category no amount of code closes.

Cheapest wins: `docs/team-plan.md`, renumber one ADR-005, add the `/v1/` prefix,
record one interaction against Azure.
