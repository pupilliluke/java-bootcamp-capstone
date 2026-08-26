# Capstone evidence index

This page keeps our final defense claims tied to real proof. Implementation
files show what we built. Test results, pipeline runs, screenshots, and logs
show what we actually exercised.

Rubric source: `CAPSTONE-BRIEF-AND-RUBRIC.md`
([open](https://github.com/Innovation-In-Software/bc-sw-engineer-java-participant/blob/main/labs/Week%206%20-%20Capstone%20Project/CAPSTONE-BRIEF-AND-RUBRIC.md#83-final-team-evaluation-4-point-weighted-scale))

We should update this page whenever new proof lands. Lab 52 will use it for the
final self-score.

## Status key

| Status | Meaning |
| ------ | ------- |
| **Proven** | A reviewer can open repeatable or executed proof for the full claim. |
| **Partial** | Useful proof exists and a named gap remains. |
| **Missing** | We still need usable proof. Leave this claim out of the defense for now. |

## Rubric coverage

Each line below comes from the final weighted team rubric.

| Rubric criterion | Weight | Status | Related claims |
| ---------------- | -----: | ------ | -------------- |
| Full-Stack Architecture & Planning | 15% | **Partial** | `ARCH-01` ([open](#arch-01-system-boundaries-and-current-architecture)), `PLAN-01` ([open](#plan-01-backlog-risks-and-course-coverage)), `PLAN-02` ([open](#plan-02-measurable-nfrs-and-adrs)) |
| Backend Services & Messaging | 20% | **Partial** | `BE-01` ([open](#be-01-layered-and-validated-spring-api)), `MSG-01` ([open](#msg-01-versioned-kafka-flow)), `DATA-01` ([open](#data-01-durable-interaction-write-and-read-back)) |
| Frontend & Persistence | 15% | **Partial** | `UI-01` ([open](#ui-01-react-agent-journey)), `DATA-01` ([open](#data-01-durable-interaction-write-and-read-back)), `DATA-02` ([open](#data-02-customer-persistence)) |
| CI/CD, Containers & Deployment | 15% | **Partial** | `CI-01` ([open](#ci-01-repeatable-pipeline)), `IMG-01` ([open](#img-01-tested-non-root-container-image)), `DEPLOY-01` ([open](#deploy-01-k3d-deploy-smoke-and-rollback)) |
| Testing & Observability | 10% | **Partial** | `TEST-01` ([open](#test-01-automated-test-layers)), `OBS-01` ([open](#obs-01-health-logs-and-correlation)) |
| Demonstration Scenario & Recovery | 10% | **Partial** | `DEMO-01` ([open](#demo-01-agent-journey-and-controlled-recovery)), `DEMO-02` ([open](#demo-02-timed-demo-and-fallback-pack)) |
| Security & Operational Hygiene | 5% | **Partial** | `SEC-01` ([open](#sec-01-jwt-and-role-enforcement)), `SEC-02` ([open](#sec-02-secrets-scanning-and-residual-findings)) |
| Documentation & Repository Quality | 5% | **Partial** | `DOC-01` ([open](#doc-01-reproducible-project-documentation)), `PLAN-02` ([open](#plan-02-measurable-nfrs-and-adrs)) |
| Presentation & Communication | 5% | **Missing** | `PRES-01` ([open](#pres-01-defense-packet-and-reflections)) |

## Claim map

### ARCH-01: System boundaries and current architecture

**Status:** Proven

We can show the CRM at context, container, messaging, persistence, and
deployment levels. The context diagram includes the trust boundaries, and the
architecture page lists the current gaps.

Files:

- `docs/architecture/context.md` ([open](../docs/architecture/context.md))
- `docs/architecture.md` ([open](../docs/architecture.md))

### PLAN-01: Backlog, risks, and course coverage

**Status:** Partial

The project has a build order, risk register, and course coverage checklist.
Some high risks still need an owner or a completed mitigation.

Files:

- `docs/backlog.md` ([open](../docs/backlog.md))
- `docs/risk-register.md` ([open](../docs/risk-register.md))
- `docs/lab-coverage.md` ([open](../docs/lab-coverage.md))

### PLAN-02: Measurable NFRs and ADRs

**Status:** Missing

We still need measurable quality targets and decision records with alternatives
and consequences.

Work items:

- NFR issue #37 ([open](https://github.com/pupilliluke/java-bootcamp-capstone/issues/37))
- ADR issue #32 ([open](https://github.com/pupilliluke/java-bootcamp-capstone/issues/32))

### BE-01: Layered and validated Spring API

**Status:** Proven

The controllers use services and repositories. The automated checks cover
validation, authentication, business rules, and API errors.

Files and runs:

- `backend/src/test/java/com/capstone/crm/api/CustomerControllerTest.java` ([open](../backend/src/test/java/com/capstone/crm/api/CustomerControllerTest.java))
- `backend/src/test/java/com/capstone/crm/api/RegistrationControllerTest.java` ([open](../backend/src/test/java/com/capstone/crm/api/RegistrationControllerTest.java))
- `backend/src/test/java/com/capstone/crm/service/RegistrationServiceTest.java` ([open](../backend/src/test/java/com/capstone/crm/service/RegistrationServiceTest.java))
- `backend/src/main/java/com/capstone/crm/exception/GlobalExceptionHandler.java` ([open](../backend/src/main/java/com/capstone/crm/exception/GlobalExceptionHandler.java))
- Successful develop pipeline run #36 ([open](https://github.com/pupilliluke/java-bootcamp-capstone/actions/runs/32908913701))

### MSG-01: Versioned Kafka flow

**Status:** Partial

Interaction events use a versioned contract and customer key. Tests cover
publishing, consuming, duplicate IDs, invalid events, and the dead letter topic.

The processed-event store still lives in memory. Successful consumer processing
currently produces a log entry.

Files and runs:

- `backend/src/test/java/com/capstone/crm/messaging/InteractionMessagingIT.java` ([open](../backend/src/test/java/com/capstone/crm/messaging/InteractionMessagingIT.java))
- `backend/src/test/java/com/capstone/crm/messaging/consumer/InteractionEventConsumerTest.java` ([open](../backend/src/test/java/com/capstone/crm/messaging/consumer/InteractionEventConsumerTest.java))
- `backend/src/test/java/com/capstone/crm/messaging/producer/InteractionEventProducerTest.java` ([open](../backend/src/test/java/com/capstone/crm/messaging/producer/InteractionEventProducerTest.java))
- Kafka test and verify screenshots in PR #2 ([open](https://github.com/pupilliluke/java-bootcamp-capstone/pull/2))
- Current messaging limits in `docs/architecture.md` ([open](../docs/architecture.md#messaging-today-and-the-target))

### DATA-01: Durable interaction write and read-back

**Status:** Proven on `develop`

An accepted interaction is stored before publication. The browser journey reads
the interaction again after leaving and reopening the customer screen.

Files and runs:

- `backend/src/main/resources/db/migration/V2__interaction.sql` ([open](https://github.com/pupilliluke/java-bootcamp-capstone/blob/develop/backend/src/main/resources/db/migration/V2__interaction.sql))
- `backend/src/test/java/com/capstone/crm/api/InteractionControllerTest.java` ([open](https://github.com/pupilliluke/java-bootcamp-capstone/blob/develop/backend/src/test/java/com/capstone/crm/api/InteractionControllerTest.java))
- `frontend/e2e/customer-journey.spec.ts` ([open](https://github.com/pupilliluke/java-bootcamp-capstone/blob/develop/frontend/e2e/customer-journey.spec.ts))
- PR #47 implementation and test record ([open](https://github.com/pupilliluke/java-bootcamp-capstone/pull/47))
- Successful merge pipeline run #33 ([open](https://github.com/pupilliluke/java-bootcamp-capstone/actions/runs/32904809334))

### UI-01: React agent journey

**Status:** Proven

An authenticated agent can move through the customer screens, create and view a
customer, and record an interaction. The UI also has loading, error, and
role-aware behavior.

Files and runs:

- `frontend/src/pages/CustomerWorkspace.tsx` ([open](../frontend/src/pages/CustomerWorkspace.tsx))
- `frontend/src/pages/CustomerDetailsPage.test.tsx` ([open](../frontend/src/pages/CustomerDetailsPage.test.tsx))
- `frontend/src/pages/CustomerFormPage.test.tsx` ([open](../frontend/src/pages/CustomerFormPage.test.tsx))
- `frontend/src/api/customers.test.ts` ([open](../frontend/src/api/customers.test.ts))
- UI screenshots and test results in PR #51 ([open](https://github.com/pupilliluke/java-bootcamp-capstone/pull/51))
- Successful develop pipeline run #36 ([open](https://github.com/pupilliluke/java-bootcamp-capstone/actions/runs/32908913701))

### DATA-02: Customer persistence

**Status:** Missing

Customer data currently lives in an in-memory map and resets with the backend.
This claim needs a JPA repository, migration, and restart proof.

Files:

- `backend/src/main/java/com/capstone/crm/repository/CustomerRepository.java` ([open](../backend/src/main/java/com/capstone/crm/repository/CustomerRepository.java))
- Architecture gaps ([open](../docs/architecture.md#gaps))

### CI-01: Repeatable pipeline

**Status:** Proven on `develop`

The pipeline runs backend, frontend, image, manifest, cluster, and browser
checks. Failed jobs keep their reports, and release artifacts carry a commit
identity.

Files and runs:

- `.github/workflows/ci.yml` ([open](https://github.com/pupilliluke/java-bootcamp-capstone/blob/develop/.github/workflows/ci.yml))
- Successful develop run #36 ([open](https://github.com/pupilliluke/java-bootcamp-capstone/actions/runs/32908913701))
- `docs/k8s-ci-review.md` ([open](https://github.com/pupilliluke/java-bootcamp-capstone/blob/develop/docs/k8s-ci-review.md))
- `docs/k8s-ci-evidence.txt` ([open](https://github.com/pupilliluke/java-bootcamp-capstone/blob/develop/docs/k8s-ci-evidence.txt))

### IMG-01: Tested non-root container image

**Status:** Proven

The backend image uses a multi-stage build, runs as UID 10001, pins base images
by digest, excludes build tools and credentials, exposes health, and has
structure and runtime tests.

Files and runs:

- `backend/Dockerfile` ([open](../backend/Dockerfile))
- `backend/container-structure-test.yaml` ([open](../backend/container-structure-test.yaml))
- `backend/src/test/java/com/capstone/crm/container/ContainerImageIT.java` ([open](../backend/src/test/java/com/capstone/crm/container/ContainerImageIT.java))
- Four-layer verification in PR #45 ([open](https://github.com/pupilliluke/java-bootcamp-capstone/pull/45))
- Image job in successful run #36 ([open](https://github.com/pupilliluke/java-bootcamp-capstone/actions/runs/32908913701))

### DEPLOY-01: k3d deploy, smoke, and rollback

**Status:** Proven on `develop`

The Kubernetes checks validate the manifests and deploy them to a real k3d
cluster. The smoke script checks health, login, role enforcement, a failed
rollout, and recovery with `rollout undo`.

The recorded failure uses a missing image. A future rehearsal should use two
working application versions.

Files and runs:

- `k8s/` ([open](https://github.com/pupilliluke/java-bootcamp-capstone/tree/develop/k8s))
- `k8s/smoke.sh` ([open](https://github.com/pupilliluke/java-bootcamp-capstone/blob/develop/k8s/smoke.sh))
- `docs/rollback-runbook.md` ([open](https://github.com/pupilliluke/java-bootcamp-capstone/blob/develop/docs/rollback-runbook.md))
- Successful cluster pipeline run #36 ([open](https://github.com/pupilliluke/java-bootcamp-capstone/actions/runs/32908913701))

### TEST-01: Automated test layers

**Status:** Proven on `develop`

CI runs backend unit, API, and integration tests, frontend component tests,
container checks, and a Playwright browser journey.

Files and runs:

- `backend/src/test/` ([open](https://github.com/pupilliluke/java-bootcamp-capstone/tree/develop/backend/src/test))
- `frontend/src/` component tests ([open](https://github.com/pupilliluke/java-bootcamp-capstone/tree/develop/frontend/src))
- `frontend/e2e/customer-journey.spec.ts` ([open](https://github.com/pupilliluke/java-bootcamp-capstone/blob/develop/frontend/e2e/customer-journey.spec.ts))
- Successful run #36 with downloadable reports ([open](https://github.com/pupilliluke/java-bootcamp-capstone/actions/runs/32908913701))

### OBS-01: Health, logs, and correlation

**Status:** Partial

The application has liveness and readiness checks, request logging, and
correlation IDs through the browser-to-API journey.

We still need dashboard evidence, consumer lag evidence, and one sanitized trace
that follows a correlation ID through the full flow.

Files:

- `backend/src/main/java/com/capstone/crm/observability/CorrelationIdFilter.java` ([open](../backend/src/main/java/com/capstone/crm/observability/CorrelationIdFilter.java))
- `backend/src/main/java/com/capstone/crm/observability/RequestLoggingFilter.java` ([open](../backend/src/main/java/com/capstone/crm/observability/RequestLoggingFilter.java))
- Playwright correlation assertions ([open](https://github.com/pupilliluke/java-bootcamp-capstone/blob/develop/frontend/e2e/customer-journey.spec.ts))
- Kubernetes probe configuration ([open](https://github.com/pupilliluke/java-bootcamp-capstone/blob/develop/k8s/deployment.yaml))

### DEMO-01: Agent journey and controlled recovery

**Status:** Proven on `develop`

The core agent journey and a controlled deployment failure have repeatable
scripts and recorded results.

Files:

- `frontend/e2e/customer-journey.spec.ts` ([open](https://github.com/pupilliluke/java-bootcamp-capstone/blob/develop/frontend/e2e/customer-journey.spec.ts))
- `k8s/smoke.sh` ([open](https://github.com/pupilliluke/java-bootcamp-capstone/blob/develop/k8s/smoke.sh))
- `docs/rollback-runbook.md` ([open](https://github.com/pupilliluke/java-bootcamp-capstone/blob/develop/docs/rollback-runbook.md))

### DEMO-02: Timed demo and fallback pack

**Status:** Missing

We still need a timed speaker and operator script, prepared synthetic fixtures,
a failure beat, and scrubbed fallback screenshots and logs.

Expected files:

- `defense/demo-script.md`
- `defense/notes/`
- Lab 52 checklist ([open](../docs/lab-coverage.md#lab-52--final-defense))

### SEC-01: JWT and role enforcement

**Status:** Proven

The backend validates JWTs and enforces the anonymous, AGENT, and ADMIN access
rules. Backend authorization remains the security boundary for controls hidden
by the frontend.

Files and screenshots:

- `backend/src/main/java/com/capstone/crm/config/SecurityConfig.java` ([open](../backend/src/main/java/com/capstone/crm/config/SecurityConfig.java))
- `backend/src/test/java/com/capstone/crm/security/SecurityRulesTest.java` ([open](../backend/src/test/java/com/capstone/crm/security/SecurityRulesTest.java))
- `backend/src/test/java/com/capstone/crm/security/AppUserAuthTest.java` ([open](../backend/src/test/java/com/capstone/crm/security/AppUserAuthTest.java))
- `backend/src/test/java/com/capstone/crm/api/AdminUserControllerTest.java` ([open](../backend/src/test/java/com/capstone/crm/api/AdminUserControllerTest.java))
- RBAC test and admin UI screenshots in PR #40 ([open](https://github.com/pupilliluke/java-bootcamp-capstone/pull/40))

### SEC-02: Secrets, scanning, and residual findings

**Status:** Partial

Secrets arrive through runtime configuration. Tests check that credentials stay
out of the image. CI also reports vulnerable packages and credential-shaped
content.

Trivy currently reports findings without blocking the pipeline. The recorded
high and critical findings still need remediation or a dated acceptance with an
owner.

Files:

- `.env.example` ([open](../.env.example))
- `backend/container-structure-test.yaml` ([open](../backend/container-structure-test.yaml))
- Trivy configuration in `.github/workflows/ci.yml` ([open](https://github.com/pupilliluke/java-bootcamp-capstone/blob/develop/.github/workflows/ci.yml))
- `docs/risk-register.md` ([open](../docs/risk-register.md))

### DOC-01: Reproducible project documentation

**Status:** Proven

A new engineer can understand the system, configure it safely, run it locally,
execute the test suites, and diagnose common setup problems.

Files:

- `README.md` ([open](../README.md))
- `docs/architecture.md` ([open](../docs/architecture.md))
- `docs/azure-admin-runbook.md` ([open](../docs/azure-admin-runbook.md))
- `scripts/verify-setup.mjs` ([open](../scripts/verify-setup.mjs))

### PRES-01: Defense packet and reflections

**Status:** Missing

The evidence index is ready. The final presentation, timed demo, technical Q&A,
retrospective, individual reflections, and self-assessment still need to be
written.

Work items:

- Retrospective and reflection issue #34 ([open](https://github.com/pupilliluke/java-bootcamp-capstone/issues/34))
- Lab 52 checklist ([open](../docs/lab-coverage.md#lab-52--final-defense))

Expected files:

- `defense/final-presentation.pdf`
- `defense/demo-script.md`
- `defense/technical-q-and-a.md`
- `defense/retrospective.md`
- `defense/self-assessment.md`

## Proof by type

Issue #35 asks for tests, pipeline runs, screenshots, and logs. Here is the
current set.

| Type | Current proof | Next update |
| ---- | ------------- | ----------- |
| Tests | Successful run #36 ([open](https://github.com/pupilliluke/java-bootcamp-capstone/actions/runs/32908913701)) includes backend, container, frontend, and browser reports. | Keep the final release run and its downloadable artifacts available through the defense. |
| Pipeline runs | Develop run #36 ([open](https://github.com/pupilliluke/java-bootcamp-capstone/actions/runs/32908913701)) and interaction merge run #33 ([open](https://github.com/pupilliluke/java-bootcamp-capstone/actions/runs/32904809334)). | Add the final tagged release run. |
| Screenshots | Kafka verification in PR #2 ([open](https://github.com/pupilliluke/java-bootcamp-capstone/pull/2)), RBAC and admin UI in PR #40 ([open](https://github.com/pupilliluke/java-bootcamp-capstone/pull/40)), and customer UI in PR #51 ([open](https://github.com/pupilliluke/java-bootcamp-capstone/pull/51)). | Copy the final scrubbed fallback images into `defense/notes/`. |
| Logs | Kubernetes mutation output ([open](https://github.com/pupilliluke/java-bootcamp-capstone/blob/develop/docs/k8s-ci-evidence.txt)), rollback observations ([open](https://github.com/pupilliluke/java-bootcamp-capstone/blob/develop/docs/rollback-runbook.md)), and pipeline artifacts. | Add one sanitized demo log that follows a correlation ID through the backend and Kafka consumer. |

## Open gaps

These are the current limits of the evidence:

- Customer records still use an in-memory repository and reset with the
  backend.
- Consumer duplicate tracking still uses in-memory state. The current handler
  writes a log entry.
- Trivy reports findings and allows the pipeline to continue.
- CI and the frontend currently use Node 20. The rubric names Node 22.
- The proven deployment is a disposable k3d environment. Azure currently hosts
  PostgreSQL.
- The rollback rehearsal uses a missing replacement image. A
  version-to-version promotion and rollback still needs proof.
- The Lab 52 presentation, demo script, Q&A, retrospective, reflections, and
  self-assessment still need to be prepared.

## Before Lab 52

- [ ] Bring this branch up to date with `develop`.
- [ ] Replace moving `develop` links with the final release tag or commit.
- [ ] Add the final successful pipeline run and preserve its reports.
- [ ] Add scrubbed fallback screenshots and a correlation trace log.
- [ ] Review every **Partial** and **Missing** row before assigning the
      self-score.
- [ ] Ask a teammate to open every link from a fresh clone.
