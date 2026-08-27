# Non-Functional Requirements — Neural

> Measurable quality targets for the Customer Management Platform (issue #37).
> Every NFR states a **metric + target + how it is measured + environment**. No vague
> adjectives — a target with no measurement method is not an NFR.
>
> **Environment key:** `local` = docker-compose stack (Postgres 17 + Kafka) with the
> Spring Boot backend on `:8080` and the Vite frontend on `:5173`. `cluster` = k3s
> deployment from `k8s/`.

## Summary

| # | Category | Target | Measured by | Environment | Status |
| - | -------- | ------ | ----------- | ----------- | ------ |
| 1 | Performance | p95 < 300 ms for `GET /api/v1/customers` and `POST /api/v1/interactions` | timed requests / `/actuator/metrics` | local | Target — to validate |
| 2 | Security | Unauthenticated write → **401**; authenticated wrong-role → **403** | `SecurityRulesTest`, `AppUserAuthTest` | local (CI) | ✅ Verified |
| 3 | Traceability | 100% of requests carry a correlation ID, propagated to logs and to the Kafka `InteractionEvent` | `CorrelationIdFilter` + log/event inspection | local | ✅ Implemented |
| 4 | Recoverability | Zero customer/interaction data loss across a backend restart; rollback to the prior image digest | restart proof + `docs/rollback-runbook.md` + `k8s/smoke.sh` | local / cluster | ✅ After #53 (customer persistence) |
| 5 | Operability | `/actuator/health` returns `UP` with `liveness` + `readiness` groups; k3s probes gate traffic | health endpoint + `k8s/deployment.yaml` probes | local / cluster | ✅ Verified |
| 6 | Accessibility & Privacy | Labelled, keyboard-navigable forms with loading/error/empty states; synthetic data only; secrets from env | component tests; `.env` gitignored | local | ✅ Mostly |

---

## 1. Performance

- **Metric:** p95 request latency.
- **Target:** < 300 ms for `GET /api/v1/customers` (list) and `POST /api/v1/interactions` (create),
  single-node local stack, warm JVM, ≤ 100 seeded rows.
- **How measured:** repeated timed requests against `:8080` (e.g. a short `curl`/k6 loop),
  cross-checked against `GET /actuator/metrics/http.server.requests`.
- **Environment:** `local`.
- **Status:** Target set; the number is the one open cell to validate under Lab 50/51 load.
- **Rationale:** an agent-facing CRM must feel responsive; 300 ms p95 keeps a search-and-open
  interaction under the ~1 s perception threshold.

## 2. Security

- **Metric:** HTTP status on unauthorized access.
- **Target:** unauthenticated write to a protected route (`POST /api/v1/customers`,
  `POST /api/v1/interactions`) returns **401**; an authenticated user without the required role
  (e.g. `AGENT` calling an admin-only route) returns **403**. Deny-by-default.
- **How measured:** `SecurityRulesTest` and `AppUserAuthTest` (negative-path assertions),
  run in CI on every push.
- **Environment:** `local` / CI.
- **Status:** ✅ **Verified** — both suites are green (part of the 79 backend tests).

## 3. Traceability

- **Metric:** proportion of requests and emitted events that carry a correlation ID.
- **Target:** 100%. Each inbound request is assigned a correlation ID by `CorrelationIdFilter`,
  which appears in structured logs and is propagated onto the Kafka `InteractionEvent` so a
  single business action is traceable UI → API → DB → event.
- **How measured:** inspect backend logs and the consumed `InteractionEvent` for a request
  tagged with a known correlation id (e.g. `lab-request-001`).
- **Environment:** `local`.
- **Status:** ✅ Implemented (`CorrelationIdFilter`, `RequestLoggingFilter`).

## 4. Recoverability

- **Metric:** data retained across restart; ability to roll back a release.
- **Target:** **zero** customer/interaction data loss when the backend restarts (state lives in
  Postgres via Flyway `V2__interaction.sql` and `V3__customer.sql`, not in memory). A bad
  release can be rolled back to the previous immutable image digest.
- **How measured:** create a record in the UI → confirm the row in Postgres → restart the
  backend → confirm the row survives. Rollback per `docs/rollback-runbook.md`; cluster health
  re-checked with `k8s/smoke.sh`.
- **Environment:** `local` / `cluster`.
- **Status:** ✅ for interactions today; ✅ for customers once **#53** (JPA `Customer` + V3) merges.

## 5. Operability

- **Metric:** health signal and probe behaviour.
- **Target:** `GET /actuator/health` returns `{"status":"UP"}` exposing `liveness` and
  `readiness` groups; k3s liveness/readiness probes gate traffic so a not-ready pod receives
  none.
- **How measured:** curl the health endpoint; inspect probe config in `k8s/deployment.yaml`;
  `k8s/smoke.sh` after deploy.
- **Environment:** `local` / `cluster`.
- **Status:** ✅ **Verified** — health returns `UP` with liveness/readiness groups.

## 6. Accessibility & Privacy

- **Metric:** form usability and data hygiene.
- **Target:** every form field is labelled and keyboard-navigable; each screen renders explicit
  loading, error, and empty states; only synthetic data (`@example.test`) is used; no secret
  (JWT signing key, DB password) is committed — all read from a gitignored `.env`.
- **How measured:** frontend component tests assert the four states per screen; manual keyboard
  pass; `git` history / `.gitignore` confirm no secrets.
- **Environment:** `local`.
- **Status:** ✅ Mostly — state coverage is in the 44 frontend tests; a formal axe/a11y sweep is
  the remaining step.

---

## Notes

- Targets marked "to validate" have a defined measurement method but no captured number yet;
  that evidence is produced in Labs 50–51 and linked from `defense/evidence-index.md` (#35).
- This document is the source for the NFR rows of the evidence index — keep the two in sync.
