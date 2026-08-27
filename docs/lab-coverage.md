# Lab coverage

Everything the course taught, and whether our capstone uses it. The rubric asks
us to "integrate Weeks 1–5 into one defendable delivery", so this is the
checklist for that.

Tick things off as they land. Being honest here is worth more than looking good —
an accurate list tells us what to build next, and it is the answer when someone
at the defense asks "did you use what you learned?"

**Key:** `[x]` done · `[~]` partly done · `[ ]` not yet · **(stretch)** unlikely
in six weeks, and fine to skip

**Where we are: 70 done, 11 partly done, 26 to go, across 53 labs.** Seven of
the remaining are marked stretch, so the realistic target is closer to 100 than
107. Week 5 delivery has landed; what is left is a mix of observability odds
and ends, contract polish (#82–#86), and the Lab 52 defense packet.

---

## Week 1 — Java and the JVM

### Lab 0 — Development environment setup
- [x] A toolchain everyone shares: JDK 21, Maven, Docker
- [x] Versions pinned so builds match — we enforce this in the build itself

### Lab 1 — JVM and compilation
- [x] Compile and run from source, understand the classpath
- [ ] Explain what the JVM does with our jar at the defense

### Lab 2 — Java syntax and input/output
- [x] Core syntax and types used everywhere in the backend
- [x] Reading and writing data at the edges of the app

### Lab 3 — Object-oriented design
- [x] Classes, encapsulation, and inheritance in the domain model
- [x] Behaviour lives with the data it belongs to

### Lab 4 — Memory management and garbage collection
- [ ] Set container memory limits from real measurements, not guesses
- [ ] Explain why our pod has the limits it has

### Lab 5 — Collections framework
- [x] Choosing the right collection for the job
- [x] Concurrent collections where more than one thread touches the data

### Lab 6 — Streams and lambdas
- [x] Mapping entities to DTOs with streams
- [ ] Grouping and summarising for a reports endpoint

### Lab 7 — Exception handling
- [x] Custom exceptions carrying meaning, not just a message
- [x] Failing loudly at startup instead of running on bad config

---

## Week 2 — Backend, AI tools and testing

### Lab 8 — Project structure
- [x] Packages by responsibility: api, service, repository, entity
- [x] One backend module, buildable on its own

### Lab 9 — Maven build and dependencies
- [x] Dependencies and plugins managed in one pom
- [x] A wrapper so everyone builds with the same Maven

### Lab 10 — Copilot fundamentals
- [x] AI assistance for boilerplate
- [x] Note in the defense where AI helped and where we rejected its output —
      defense/ai-assistance.md, from the container work

### Lab 11 — Copilot for testing and refactoring
- [x] AI-assisted test writing
- [ ] Review discipline: nothing merges unread

### Lab 12 — Coding standards and refactoring
- [~] A consistent style across the codebase
- [ ] Agreed standards written down somewhere

### Lab 13 — SOAP API design, contract first
- [ ] A WSDL-first contract **(stretch)**
- [ ] Contract-first thinking applied to our REST DTOs instead

### Lab 14 — DTOs and validation
- [x] Request and response DTOs at the API boundary
- [x] Bean Validation on incoming requests

### Lab 15 — Service layer design
- [x] Business rules in services, not controllers
- [~] Status transition rules enforced in one place

### Lab 16 — API exception handling
- [x] One handler turning exceptions into consistent responses
- [x] Errors that never leak internals to the caller

### Lab 17 — JUnit testing
- [x] Unit tests for services and security rules
- [x] Tests that fail loudly rather than silently skipping

### Lab 18 — Mockito and mocking
- [x] Mocked collaborators to isolate the unit under test
- [x] Verifying interactions, not just return values

### Lab 19 — Integration and UI testing with Selenium
- [ ] Browser regression suite **(stretch)**
- [x] At least one automated end-to-end journey — Playwright's
      `customer-journey.spec.ts` runs in CI's e2e job: create a customer, log
      an interaction, read it back, correlation ids asserted per call

### Lab 20 — Structured logging
- [x] Correlation IDs flowing through the log pattern —
      `CorrelationIdFilter` fills the MDC for every HTTP request and echoes the
      id back; `RequestLoggingFilter` writes one line per request carrying it
- [ ] One correlation ID followed from the browser to the consumer

### Lab 21 — Observability and monitoring
- [x] Actuator health, with liveness and readiness probes
- [ ] Metrics worth looking at during the demo

---

## Week 3 — Spring and enterprise patterns

### Lab 22 — Spring IoC and dependency injection
- [x] Constructor injection throughout
- [x] No field injection, no manual wiring

### Lab 23 — Spring Boot setup and auto-configuration
- [x] Boot starters doing the wiring
- [x] Auto-configuration overridden only where we mean it

### Lab 24 — SOAP endpoints with Spring-WS
- [ ] A SOAP endpoint alongside REST **(stretch)**

### Lab 25 — Service and repository layers
- [x] Repository interfaces separate from business logic
- [x] Every entity behind a repository, not a map — customers joined users and
      interactions on JPA when `V3__customer.sql` landed

### Lab 26 — Spring profiles and configuration
- [x] Profiles for local, azure and test
- [x] Configuration from the environment, never hardcoded

### Lab 27 — Transaction management
- [~] Read-only transactions on lookups
- [x] Write and publish inside one transaction — as a deliberate dual write,
      documented with its failure modes in ADR-008

### Lab 28 — Spring Security basics
- [x] Real JWT with issuer, role and expiry
- [x] Role-based rules on every route, with tests proving the refusals

### Lab 29 — Validation and exception handling
- [x] A consistent error body across the API
- [x] The same answer for unknown user and wrong password, so logins cannot be probed

---

## Week 4 — Kafka, React, PostgreSQL and resilience

### Lab 30 — Event-driven architecture with Kafka
- [x] Versioned topic and a safe producer
- [x] Customer ID as the message key, so one customer's events stay ordered

### Lab 31 — Spring Boot integration with Kafka
- [x] Listener with idempotency and a dead letter topic
- [~] Idempotency that survives a restart — currently in memory only

### Lab 32 — Resilience4j for outbound calls
- [ ] Circuit breaker and retry on outbound calls **(stretch)**
- [ ] WireMock proving the behaviour **(stretch)**

### Lab 33 — React components
- [x] Typed, presentational components
- [~] Accessibility checked, not assumed

### Lab 34 — React state and events
- [x] Controlled forms with client-side validation
- [ ] Create, edit and delete in the UI

### Lab 35 — Integrating React with the Spring API
- [x] One fetch boundary; components never call fetch directly
- [x] Honest loading, empty and error states

### Lab 36 — Front-end security
- [x] Token in memory, never in localStorage
- [~] A written security review of the SPA — the labs left this out of scope

### Lab 37 — PostgreSQL design for customers and accounts
- [~] A repeatable schema with real constraints
- [ ] The full customer domain: accounts, addresses, status history

### Lab 38 — SQL and query performance
- [ ] EXPLAIN before and after on our slowest query
- [ ] Indexes chosen from evidence, and predicates that can use them

### Lab 39 — Spring Data JPA with PostgreSQL
- [x] Flyway owning the schema, entities and repositories over real PostgreSQL
- [ ] Paging and optimistic locking on customer reads

---

## Week 5 — DevOps, CI/CD and Kubernetes

### Lab 40 — Application security testing
- [x] A scanner runs in CI — Trivy over the image, on every pull request, and
      it gates: `exit-code: "1"` on HIGH or CRITICAL. The image carries the
      Spring Boot fat jar, so `BOOT-INF/lib` is scanned along with the OS layer
- [x] Fix or accept each finding, with an owner and a date — the 8 critical and
      41 high came from spring-boot-parent 3.3.5; bumping to 3.5.16 cleared
      them. What remains is one real defect, fixed by pinning postgresql
      42.7.13, and eight Go defects in the base image's `/usr/bin/pebble`,
      each accepted by id in `.trivyignore.yaml` with an owner and a
      2026-11-26 expiry
- [x] Dependency-Check remains available as a second opinion via the
      `security-scan` Maven profile, but no longer gates CI. It matches on CPE
      strings rather than package coordinates, which produced three false
      positives (see `backend/dependency-check-suppressions.xml`) while missing
      the pgjdbc defect above — and it needs an NVD API key that could not be
      obtained

### Lab 41 — Containerise the Spring Boot app
- [x] Multi-stage Dockerfile producing a small image
- [x] Runs as a non-root user, with a pinned digest rather than a tag
- [x] Four layers of checks on the image: hadolint, container-structure-test,
      a vulnerability scan, and ContainerImageIT starting it against real
      PostgreSQL — see backend/container-structure-test.yaml and the image job

### Lab 42 — Kubernetes on k3s
- [x] Deployment, Service, ConfigMap, Secret and Ingress — under `k8s/`,
      namespace-portable, with the Secret created out-of-band
- [~] Three probes are in the manifest and CI breaks-and-recovers the deploy;
      the rehearsed rollback recovers from a missing image — a
      version-to-version rehearsal is still owed

### Lab 43 — GitHub CI/CD pipeline
- [x] Every push builds and tests
- [x] Built once, with an identity we can trace, and no secrets in logs

### Lab 44 — Continuous delivery and promotion
- [~] One artifact, never rebuilt: the publish job pushes each develop/main
      build to GHCR by commit SHA and the Deployment pins its digest — but with
      one environment there is no between-environments promotion yet
- [x] Objective gates deciding what moves forward — tests, hadolint, structure
      tests, and Trivy at `exit-code: "1"` gate every merge

### Lab 45 — Infrastructure as code
- [ ] Terraform and Ansible sketches **(stretch)**
- [ ] Human review for cost and exposure **(stretch)**

### Lab 46 — Kafka resilience and observability
- [ ] Consumer lag visible somewhere we would notice
- [ ] A written, safe replay procedure

### Lab 47 — Professional communication
- [ ] A release note a stakeholder could read
- [ ] An incident update template agreed before we need it

---

## Week 6 — Capstone

### Lab 48 — Planning and architecture
- [x] Architecture written down, backlog and risk register live
- [x] ADRs and measurable NFRs — ADR-001 to ADR-010 and `docs/nfrs.md`, every
      target with a metric, method and environment

### Lab 49 — Backend and messaging slice
- [x] Validated API, versioned event, and Flyway/JPA persistence — contract
      polish against the course spec is tracked as #82–#86
- [x] Interaction saved before the event is published — inside one
      transaction, as the dual write ADR-008 documents

### Lab 50 — Frontend and persistence
- [x] The agent journey reaching PostgreSQL end to end — the Playwright
      journey creates, records, and reads back through the real API and database
- [~] Proof the data landed: read-back is asserted in e2e; the SQL-level check
      and the restart-durability rehearsal are not yet recorded as evidence

### Lab 51 — Security, CI/CD and deployment
- [x] JWT and RBAC done; pipeline, image, publish, and cluster jobs all real
- [x] Deployed, smoke tested, broken on purpose, and rolled back — on the k3d
      cluster in CI (twelve assertions); the shared course-cluster run is still
      owed pending the k3d-versus-k3s ruling

### Lab 52 — Final defense
- [~] Evidence index is live and reconciles to the rubric; demo script and Q&A
      notes do not exist yet
- [ ] Retrospective and individual reflections

---

## How to use this

- Tick a box in the same pull request that does the work.
- If something is genuinely not happening, mark it **(stretch)** rather than
  leaving it looking like a failure. A skipped item we named beats a gap someone
  finds for us.
- The half-ticked lines are the honest ones — 12, 15, 27, 31, 33, 36, 37, 42,
  44, 50, 52 — and they are where the most credit is sitting for the least work.
