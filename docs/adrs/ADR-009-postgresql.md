# ADR-009: PostgreSQL as the system of record

- **Status:** Accepted
- **Date:** 2026-08-27 (records a decision made in week one; written down from the implementation rather than invented after it)
- **Deciders:** Team
- **Related backlog:** Issue #32

## Context

The CRM stores users, customers, and interactions. The shape is relational: interactions belong to customers, customer numbers come from a sequence (`customer_number_seq`) so deleting a customer can never free a number for reuse, and the audit story depends on constraints holding. The schema must be owned by migrations — `ddl-auto: validate` means an entity that drifts from the migration fails at startup instead of silently altering a table in Azure.

The course stack teaches PostgreSQL with Spring Data JPA and Flyway, and Azure hosts a managed PostgreSQL server for the deployed profile (ADR-003, ADR-004). The same engine has to run identically on a laptop, in CI, and hosted.

## Decision

We will use PostgreSQL as the only system of record: `postgres:17` via docker-compose locally and in CI, Azure Database for PostgreSQL when deployed, accessed through Spring Data JPA with the schema owned by Flyway.

## Alternatives considered

| Option | Pros | Cons | Why not |
| ------ | ---- | ---- | ------- |
| A: H2 everywhere | Zero setup, fastest possible startup | Not a production database; dialect and constraint behavior drift from anything we would deploy | Fine for the fast test suite (ADR-007), not for the system of record |
| B: MySQL / MariaDB | Equally capable relational engine | No course, hosting, or team-experience alignment; every lab, example, and Azure runbook here is PostgreSQL | Switching buys nothing and costs the whole support surface |
| C: Document store (MongoDB) | Flexible shape for evolving records | The domain is relational — interactions keyed to customers, sequence-assigned business ids, FK-able integrity — and the JPA + Flyway migration story disappears | Wrong shape for the data and for the rubric's persistence evidence |

## Consequences

- **Positive:** Real constraints, indexes, and sequences; one dialect from laptop to Azure; Flyway's migration chain is the schema's history and its evidence.
- **Negative / follow-ups:** The fast suite runs on H2, which can accept what PostgreSQL rejects — ADR-007's focused PostgreSQL test is the mitigation. `interaction.customer_id` is still a plain indexed column: V2 predates the customer table and promised the FK "once customers land"; customers landed in V3 and the FK has not — that is an open follow-up.
- **NFR impact:** Recoverability (NFR-4, restart durability and rollback) and performance (NFR-1's p95 targets are measured against this engine).
- **Evidence later labs will need:** Migrations `V1`–`V4` applying cleanly, `CustomerRepositoryIT` / `AppUserRepositoryIT` against real PostgreSQL, and the restart-durability rehearsal.

## Links

- Migrations: `backend/src/main/resources/db/migration/`
- Datasource and JPA settings: `backend/src/main/resources/application.yml`
- Local engine: `docker-compose.yml` (`postgres:17`)
- Profiles: `docs/adrs/ADR-003-database-profiles.md`
- Azure authentication: `docs/adrs/ADR-004-azure-database-authentication.md`
- Test strategy: `docs/adrs/ADR-007-database-test-strategy.md`
