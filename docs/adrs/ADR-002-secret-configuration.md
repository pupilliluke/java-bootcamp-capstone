# ADR-002: Secrets in environment files

- **Status:** Proposed | Accepted | Superseded
- **Date:** 08-24-2026
- **Deciders:** Luke Pupilli
- **Related backlog:** Issue #32

## Context

The application needs database credentials and a JWT signing secret in each environment. Committing those values would expose working credentials through the repository history.

Developers also need one documented set of variable names that works with Spring Boot and Docker Compose.

## Decision

We will store local secrets in a gitignored `.env` file and supply the same variable names through environment variables in deployed environments.

## Alternatives considered

| Option | Pros | Cons | Why not |
| ------ | ---- | ---- | ------- |
| A: Commit values in Spring configuration | Requires no local setup and keeps all configuration in one place | Exposes passwords and signing keys in Git history | A public repository cannot safely contain working secrets |
| B: Use `.env` locally and environment variables in deployments | Keeps secret values out of Git and gives Spring Boot and Docker Compose the same variable names | Each developer must create and protect a local `.env` file | Selected for the project |
| C: Use a managed secret service | Supports centralized rotation, access control, and auditing | Adds cloud configuration and local authentication work | The capstone does not have a shared secret service |

## Consequences

- **Positive:** Secret values stay out of the repository. Spring Boot and Docker Compose read the same local values. Deployments can use the same names without shipping a `.env` file.
- **Negative / follow-ups:** Developers must create and protect their own `.env` file. Secret changes must be shared through a secure channel. JWT secret rotation signs users out.
- **NFR impact:** This supports security and deployability. The application fails during startup when the required JWT secret is missing.
- **Evidence later labs will need:** The repository must ignore `.env`. The committed example must contain placeholders only. A deployment must show secrets supplied through runtime configuration.

## Links

- Git Ignore Rules: `.gitignore`
- Environment Template: `.env.example`
- Spring Configuration: `backend/src/main/resources/application.yml`
- Local Database Profile: `backend/src/main/resources/application-local.yml`
- Azure Database Profile: `backend/src/main/resources/application-azure.yml`
- Docker Compose Configuration: `docker-compose.yml`
