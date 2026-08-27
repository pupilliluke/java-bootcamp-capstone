# Risk register

Live document. Scores are likelihood × impact, each 1–5. Anything scoring 12 or
above needs a mitigation with a date, not just a contingency.

Owners below are drawn from who currently owns the code, not from an agreement —
confirm them at standup and correct anything wrong.

Last reviewed: 2026-08-27

## Delivery risks

| ID | Risk | L | I | Score | Trigger | Mitigation | Contingency | Owner |
| -- | ---- | - | - | ----- | ------- | ---------- | ----------- | ----- |
| R1 | Interactions are never persisted. `InteractionService` publishes to Kafka and returns, writing nothing. The rubric requires "durable customer interactions with verified persistence". | 5 | 5 | 25 | Already true today | Add an `interaction` table and write before publishing | Demo the Kafka event as the record of the interaction and state the limitation | Chase |
| R2 | The UI and API disagree on the interaction contract, so the demo's third step 404s. UI posts `/api/customers/{id}/interactions` with `{channel, summary}`; API serves `/api/interactions` with `{customerId, channel, notes}`. | 5 | 4 | 20 | Already true today | One side changes to match; decide which at standup | UI already degrades to a local-only note | Himank + Chase |
| R3 | Customers live in a `ConcurrentHashMap` and reset on every restart. A restart mid-demo loses anything created live. | 4 | 4 | 16 | Any backend restart | Convert `CustomerRepository` to JPA; the datasource and Flyway are already in place | Re-seed by restarting; only CUS-1001 and CUS-1002 are needed for the script | Tim |
| R4 | No CI pipeline, container image, or Kubernetes manifests. Three scored categories have no evidence at all. | 5 | 4 | 20 | Already true today | Lab 51 starter ships `ci.yml`, a `Dockerfile` and `k8s/deployment.yaml` | None — this cannot be faked at the defense | Unassigned |
| R5 | Knowledge is siloed by area, and each area has exactly one person who understands it. | 3 | 4 | 12 | Any absence in the final week | Walk through each area at standup; the architecture doc covers the shape | Pair on the defense so two people can answer each question | All |

**Resolved since the last review (2026-08-23).** The rows above are kept as the
record of what was true then; this is what closed them.

- **R1** — interactions persist. `V2__interaction.sql`, an `Interaction` entity
  and a `@Transactional` service that saves before publishing (#47). The dual
  write that remains is recorded in `ADR-008`.
- **R2** — the UI and API agree. The frontend calls `POST /api/interactions` with
  `{customerId, channel, notes}` and reads history back from
  `GET /api/customers/{id}/interactions`.
- **R3** — customers persist. `V3__customer.sql`, `Customer` is a JPA `@Entity`
  and `CustomerRepository` is a `JpaRepository` (#53).
- **R4** — CI, image and manifests all exist: seven jobs including a container
  build with hadolint, container-structure-test and trivy, kubeconform
  validation, and a `cluster` job that deploys to k3d and runs a smoke test.

## Technical risks

| ID | Risk | L | I | Score | Trigger | Mitigation | Contingency | Owner |
| -- | ---- | - | - | ----- | ------- | ---------- | ----------- | ----- |
| R6 | The "exactly once" guarantee is in-process only. `InMemoryProcessedEventStore` is a `ConcurrentHashMap`, so a restart or a second replica reprocesses events. | 3 | 3 | 9 | Restart, or any deployment with more than one replica | Move processed event IDs to a table | Run one replica and say so | Chase |
| R7 | The shared Azure database means one person's migration or reseed changes what everyone else sees, including during the demo. | 3 | 4 | 12 | Anyone running against Azure while another demos | Use local Docker for day-to-day work; Azure only for the demo | Fall back to local by commenting three lines in `.env` | Luke |
| R8 | Azure connection limits. Each backend opens a Hikari pool of up to 10; four developers on Azure can exhaust a burstable tier. | 3 | 3 | 9 | Several people pointed at Azure at once | Set `spring.datasource.hikari.maximum-pool-size=3` in each `.env` | Raise the tier for the demo window | Luke |
| R9 | Local PostgreSQL is 17, Azure is 18. Migrations are only ever validated against 17 before reaching 18. | 2 | 3 | 6 | A future migration using version-specific syntax | `AppUserRepositoryIT` validates migrations against real PostgreSQL, not just H2 | Fix forward; the direction (17 → 18) is the safe one | Luke |
| R10 | Tests run on H2 rather than PostgreSQL, so a query or constraint can pass the suite and fail on a real server. | 3 | 3 | 9 | Any new repository or migration | Extend `AppUserRepositoryIT` as tables are added | Manual check against the local container | All |
| R11 | Frontend runs Node 20; the rubric names Node 22. | 2 | 2 | 4 | A grader checking the stated stack | Upgrade all four machines together, or document the deviation | Explain it in the defense | Himank |
| R12 | Five npm advisories in the dev toolchain (vite, esbuild, vitest). `npm audit --omit=dev` reports zero, so nothing ships. | 2 | 2 | 4 | A SAST report at Lab 51 | Bump vite and vitest | Present the `--omit=dev` result showing no production exposure | Himank |

## Accepted risks

Recorded per §8.4 rule 6: a security finding is a hard gate *unless residual risk
is explicitly accepted with owner and date*.

| ID | Risk | Decision | Rationale | Owner | Date |
| -- | ---- | -------- | --------- | ----- | ---- |
| A1 | The Azure database accepts connections from any IP address. Firewall rule `AllowAll_2026-8-23_18-25-10` spans `0.0.0.0`–`255.255.255.255`. | Accepted for the training period | Four developers on rotating home IP addresses would need constant rule changes. The server holds only synthetic data, TLS 1.2 is enforced, and the admin password is 128 characters, so brute force is not realistic. Residual exposure is credential leakage, connection flooding, and future gateway CVEs. | Luke Pupilli | 2026-08-23 |
| A2 | `CVE-2025-7962` (7.5) against `angus-activation-2.0.3.jar`. SMTP injection via CR/LF in Jakarta Mail. | Suppressed as not applicable, expires 2026-11-26 | Two independent reasons. Wrong package: the advisory is against Jakarta Mail (`angus-mail`); this is `angus-activation`, a different artifact matched on the shared `angus` CPE. Wrong application: this service sends no mail — there is no `spring-boot-starter-mail` and no `jakarta.mail` on the classpath, so there is no SMTP path to inject into. `angus-activation` arrives as a runtime transitive of `jaxb-core`, for XML binding. | Luke Pupilli | 2026-08-26 |
| A3 | `CVE-2026-34478`, `CVE-2026-34479`, `CVE-2026-34480`, `CVE-2026-34481` (7.5 each) against `log4j-api-2.24.3.jar`. | Suppressed as not applicable, expires 2026-11-26 | All four are defects in Log4j **Core** layouts — `Rfc5424Layout`, `Log4j1XmlLayout`, `XmlLayout`, `JsonTemplateLayout` by name. Those classes ship in `log4j-core`, which is not on the classpath. Only `log4j-api` is present, pulled in under `log4j-to-slf4j`, a bridge that routes Log4j API calls into SLF4J and therefore Logback. No Log4j layout is ever instantiated because no Log4j implementation exists to instantiate one. Re-triage immediately if `log4j-core` ever appears. | Luke Pupilli | 2026-08-26 |
| A4 | The **Contacts** and **Reports** pages render hardcoded mock data (`MOCK_CONTACTS`, `MOCK_REPORTS`) with no backend behind them — there is no contacts entity or reports source to read. (The Activities page was rewired to live data in #111 and is not affected; seeding, #106, does not touch Contacts/Reports.) | Mitigated — the two sidebar links are removed (#102), so neither page is reachable during the demo | Building real Contacts/Reports is out of scope for the week, so rather than show a panel labeled-but-fabricated data, the links are dropped from the nav; the pages and routes still exist behind them and can be relinked when they have a real data source. The live paths — the Activities feed and the customer Activities tab — use real interactions. Tracked in issue #102. | Luke Pupilli | 2026-08-27 |

Review A1 before the Lab 52 defense: if the database holds anything beyond
synthetic fixtures by then, narrow the rule instead.

A2 and A3 both expire 2026-11-26. On that date the suppressions lapse and the
findings reopen, which is the difference between accepting a risk and forgetting
one. Both rationales are structural rather than judgement calls, and both are
checkable in about ten seconds:

```
./mvnw dependency:tree -Dincludes="org.eclipse.angus:*"
./mvnw dependency:tree -Dincludes="org.apache.logging.log4j:*"
```

## Dependency scan triage, 2026-08-26

The full record of what the Dependency-Check gate found and what was done about
each finding. Scan run with `./mvnw -Psecurity-scan dependency-check:check`,
threshold `failBuildOnCVSS=7`.

| Stage | Findings at CVSS ≥ 7.0 | What changed |
| ----- | ---------------------- | ------------ |
| Spring Boot 3.3.5, as it stood | 73 | Baseline. Five at 9.8. |
| Spring Boot 3.3.13 | 63 | The last release of the 3.3 line, and **still carrying `CVE-2026-40974` (9.8) in `spring-boot` itself**. No 3.3.x clears this; the line is finished. |
| Spring Boot 3.5.16 | 11 | Every Spring, Spring Security, Jackson, Kafka and PostgreSQL finding cleared. 83/83 tests pass with no code changes. |
| `tomcat.version` 10.1.59 | 5 | Boot 3.5.16 ships Tomcat 10.1.55; 10.1.59 clears all six remaining Tomcat findings, four of which were 9.1. |
| Suppressed A2 and A3 | 0 | Gate passes. |

Three decisions worth recording, because each one could reasonably have gone
another way:

**Upgrading rather than suppressing the Spring findings.** 68 of the 73 were
fixed by moving to a supported release. Suppressing them would have meant 68
written justifications for vulnerabilities that were exploitable and had patches
available, which is not triage.

**Overriding `tomcat.version` above what Boot's BOM pins.** This runs a Tomcat
that Spring Boot 3.5.16 was not tested against. Accepted because the alternative
was six live findings including four at 9.1, and because the full suite plus the
browser journey and the cluster smoke test all pass on it. Revisit at the next
Boot upgrade and drop the override if the BOM has caught up.

**Disabling the OSS Index analyzer.** It requires a Sonatype account; without one
it returns 401 and raises an `AnalysisException` that fails the build for a
reason unrelated to any vulnerability. A gate that goes red because a third party
rate-limited us teaches people to ignore it. NVD is the primary data source and
needs no account. Re-enable if someone adds credentials.

The gate is verified failable rather than assumed: it fails on Spring Boot 3.3.5,
on 3.3.13, and on 3.5.16 with the suppression file emptied.

## Notes

- R1, R2 and R4 are the three that decide the grade. Everything else is
  refinement.
- R1 and R2 together mean the headline demo journey — search, profile, record an
  interaction — cannot currently complete its third step.
