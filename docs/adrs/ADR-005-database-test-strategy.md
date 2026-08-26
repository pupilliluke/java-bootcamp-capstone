# ADR-005: H2 for the fast suite with one real-PostgreSQL test

- **Status:** Proposed | Accepted | Superseded
- **Date:** 08-25-2026
- **Deciders:** Chase Bulkin
- **Related backlog:** Issue #32

## Context

The test suite needs fast feedback on developer machines and in CI. Requiring PostgreSQL for every test would add startup time and local setup work.

H2 can run the migrations in PostgreSQL mode, though it cannot reproduce every PostgreSQL constraint and behavior.

## Decision

We will run the main test suite on H2 in PostgreSQL mode and keep a focused integration test against real PostgreSQL.

## Alternatives considered

| Option | Pros | Cons | Why not |
| ------ | ---- | ---- | ------- |
| A: Run every database test on H2 | Provides the fastest suite and needs no database service | Can miss PostgreSQL-specific SQL, constraints, and driver behavior | H2 alone cannot prove PostgreSQL compatibility |
| B: Run every database test on PostgreSQL | Gives the highest database fidelity throughout the suite | Requires a running container and increases execution time | The added cost would slow routine feedback |
| C: Use H2 with focused PostgreSQL coverage | Keeps most tests fast and verifies critical behavior on the real database engine | Requires two test configurations and deliberate PostgreSQL coverage | Selected for the project |

## Consequences

- **Positive:** Most tests run quickly without Docker. Flyway applies the production migrations to H2. The focused integration test checks PostgreSQL-specific behavior.
- **Negative / follow-ups:** H2 can accept behavior that PostgreSQL rejects. New tables and database-specific queries need focused PostgreSQL coverage. The integration test may skip when local PostgreSQL is unavailable.
- **NFR impact:** This supports fast feedback and database compatibility. CI provides the repeatable environment for the PostgreSQL check.
- **Evidence later labs will need:** Test results must show the H2 suite passing. CI must show the PostgreSQL integration test running. Migration failures must fail the build.

## Links

- Test Database Profile: `backend/src/test/resources/application.properties`
- PostgreSQL Repository Test: `backend/src/test/java/com/capstone/crm/repository/AppUserRepositoryIT.java`
- Container Integration Test: `backend/src/test/java/com/capstone/crm/container/ContainerImageIT.java`
- Maven Test Configuration: `backend/pom.xml`
- Database Risk Register: `docs/risk-register.md`
