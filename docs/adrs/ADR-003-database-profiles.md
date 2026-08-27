# ADR-003: Spring profiles for local vs Azure

- **Status:** Accepted
- **Date:** 08-24-2026
- **Deciders:** Luke Pupilli
- **Related backlog:** Issue #32

## Context

Developers use a local PostgreSQL container while the shared environment uses Azure Database for PostgreSQL. These environments require different connection settings, and the test suite uses H2.

The team needs to switch environments without editing tracked files or producing separate application builds.

## Decision

We will use Spring profiles for local, Azure, and test database configuration, with `local` as the default profile.

## Alternatives considered

| Option | Pros | Cons | Why not |
| ------ | ---- | ---- | ------- |
| A: Edit one configuration file for each environment | Uses a single file and requires no profile selection | Creates local edits and makes accidental Azure connections more likely | Tracked configuration would drift between developers |
| B: Use Spring profiles | Keeps shared settings together and selects the datasource at runtime | Requires profile-specific files and clear activation instructions | Selected for the project |
| C: Build a separate artifact for each environment | Packages each environment with isolated settings | Creates multiple artifacts and allows their code or configuration to drift | The same tested artifact should run in every environment |

## Consequences

- **Positive:** Developers can start the application against local PostgreSQL with no profile flag. Azure uses the same application build. Tests remain independent of a running database.
- **Negative / follow-ups:** A wrong profile can connect the application to the wrong database. Profile documentation must stay current. PostgreSQL version differences still require integration testing.
- **NFR impact:** This supports portability, repeatable setup, and environment isolation. Azure connections require TLS through the profile configuration.
- **Evidence later labs will need:** Local and Azure startup logs must show the expected profile. Tests must activate the test profile. Configuration files must contain placeholders in place of credentials.

## Links

- Shared Spring Configuration: `backend/src/main/resources/application.yml`
- Local Database Profile: `backend/src/main/resources/application-local.yml`
- Azure Database Profile: `backend/src/main/resources/application-azure.yml`
- Test Database Profile: `backend/src/test/resources/application.properties`
- Architecture Description: `docs/architecture.md`
