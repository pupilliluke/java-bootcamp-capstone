# Risk register

Live document. Scores are likelihood × impact, each 1–5. Anything scoring 12 or
above needs a mitigation with a date, not just a contingency.

Owners below are drawn from who currently owns the code, not from an agreement —
confirm them at standup and correct anything wrong.

Last reviewed: 2026-08-23

## Delivery risks

| ID | Risk | L | I | Score | Trigger | Mitigation | Contingency | Owner |
| -- | ---- | - | - | ----- | ------- | ---------- | ----------- | ----- |
| R1 | Interactions are never persisted. `InteractionService` publishes to Kafka and returns, writing nothing. The rubric requires "durable customer interactions with verified persistence". | 5 | 5 | 25 | Already true today | Add an `interaction` table and write before publishing | Demo the Kafka event as the record of the interaction and state the limitation | Chase |
| R2 | The UI and API disagree on the interaction contract, so the demo's third step 404s. UI posts `/api/customers/{id}/interactions` with `{channel, summary}`; API serves `/api/interactions` with `{customerId, channel, notes}`. | 5 | 4 | 20 | Already true today | One side changes to match; decide which at standup | UI already degrades to a local-only note | Himank + Chase |
| R3 | Customers live in a `ConcurrentHashMap` and reset on every restart. A restart mid-demo loses anything created live. | 4 | 4 | 16 | Any backend restart | Convert `CustomerRepository` to JPA; the datasource and Flyway are already in place | Re-seed by restarting; only CUS-1001 and CUS-1002 are needed for the script | Tim |
| R4 | No CI pipeline, container image, or Kubernetes manifests. Three scored categories have no evidence at all. | 5 | 4 | 20 | Already true today | Lab 51 starter ships `ci.yml`, a `Dockerfile` and `k8s/deployment.yaml` | None — this cannot be faked at the defense | Unassigned |
| R5 | Knowledge is siloed by area, and each area has exactly one person who understands it. | 3 | 4 | 12 | Any absence in the final week | Walk through each area at standup; the architecture doc covers the shape | Pair on the defense so two people can answer each question | All |

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

Review A1 before the Lab 52 defense: if the database holds anything beyond
synthetic fixtures by then, narrow the rule instead.

## Notes

- R1, R2 and R4 are the three that decide the grade. Everything else is
  refinement.
- R1 and R2 together mean the headline demo journey — search, profile, record an
  interaction — cannot currently complete its third step.
