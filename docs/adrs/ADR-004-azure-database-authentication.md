# ADR-004: Password login instead of Microsoft Entra

- **Status:** Proposed | Accepted | Superseded
- **Date:** 08-25-2026
- **Deciders:** Chase Bulkin
- **Related backlog:** Issue #32

## Context

What force is driving this decision? (scale, consistency, security, ops, team skill, timebox)

The application can connect to Azure Database for PostgreSQL using PostgreSQL credentials or Microsoft Entra authentication. The team currently runs the backend from developer laptops. Entra access tokens expire after a proximately one hour. Supporting them reliably would require the  application to request fresh tokens as new database connections are created.  Each developer would also need an Azure identity, database access, and an active  Azure CLI session.

The capstone schedule prioritizes persistence, testing, deployment, and defense evidence. The Azure database contains synthetic training data.
_____

## Decision

We will implement authentication with username and password login connected to Azure PostgreSQL over using Azure Entra.

## Alternatives considered

| Option                     | Pros                                                  | Cons                                                                    | Why not                                              |
|----------------------------|-------------------------------------------------------|-------------------------------------------------------------------------|------------------------------------------------------|
| A — Entra user tokens      | Removes a permanent database password                 | Tokens expire and require refresh logic                                 | Too much operational work for the capstone schedule  |
| B — Managed identity       | Removes database credentials managed by the app       | Requires an Azure-hosted workload and additional identity configuration | The application currently runs from developer laptops                                                |
| C — PostgreSQL credentials | Simple JDBC configuration and familiar local workflow | Requires password storage, distribution, and rotation                   | Selected for the training environment                                                |

## Consequences

- **Positive:** Every developer can use the same application build and JDBC configuration pattern.
- **Negative and follow-up work:** The password must be distributed securely and rotated after exposure.
- **NFR impact:** TLS protects the connection. Runtime configuration keeps the credential out of Git.
- **Evidence:** The Azure profile requires the database values and enables `sslmode=require`.
- 
## Links

- Azure Auth: `docs/azure-authentication.md`
- Azure docker connection: `backend/src/main/resources/application-azure.yml`
- Environment: `.env.example`