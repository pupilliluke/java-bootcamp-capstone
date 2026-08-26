# Plan checklist

Lab 48 close-out. Marked against the repository on 2026-08-25. Detail and the
Labs 49 to 52 picture are in `docs/week6-gap-analysis.md`.

## Planning (Lab 48)

| # | Confirm | State |
| - | ------- | ----- |
| 1 | Context and container architecture docs | Fail — `docs/architecture/container.md` done, `context.md` missing |
| 2 | Measurable NFRs | Pass — `docs/nfrs.md` |
| 3 | Prioritized backlog with acceptance criteria | Pass — `docs/backlog.md` |
| 4 | ADRs for major decisions | Fail — 1 of 5. `ADR-005-deploy.md` only |
| 5 | Risk register with owners and dates | Pass — `docs/risk-register.md` |

## Hygiene

| # | Confirm | State |
| - | ------- | ----- |
| 1 | No secrets in Git | Pass |
| 2 | Fictional data only | Pass |
| 3 | Pushes to our own remote | Pass |

## Outstanding

| Item | Blocked on |
| ---- | ---------- |
| `docs/architecture/context.md` | Users, journeys, exclusions and the IdP boundary are product decisions |
| ADR-001 PostgreSQL | Read the Flyway and profile setup |
| ADR-002 Kafka | Read the producer, consumer and DLT wiring |
| ADR-003 Consistency | **Undecided.** After-commit versus outbox is a team call |
| ADR-004 JWT | Read `SecurityConfig` and `JwtService` |
| `docs/team-plan.md` | Owners and dates |
