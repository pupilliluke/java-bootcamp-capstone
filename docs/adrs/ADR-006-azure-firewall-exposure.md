# ADR-006: Accepting the open Azure firewall

- **Status:** Proposed | Accepted | Superseded
- **Date:** 08-25-2026
- **Deciders:** Chase Bulkin
- **Related backlog:** Issue #32

## Context

Four developers need to reach the shared Azure PostgreSQL server from home networks with changing public IP addresses. Per-IP firewall rules would require frequent updates during the capstone.

The database contains synthetic training data. TLS is required, and access still requires the database credentials.

## Decision

We will accept the open Azure PostgreSQL firewall rule for the training period and review it before the Lab 52 defense.

## Alternatives considered

| Option | Pros | Cons | Why not |
| ------ | ---- | ---- | ------- |
| A: Allow connections from all public IP addresses | Gives every teammate reliable access as home addresses change | Exposes the database endpoint to internet scanning, connection floods, and credential attacks | Selected temporarily for the training environment |
| B: Maintain one firewall rule per developer | Reduces network exposure to known addresses | Requires repeated updates when home addresses change | The maintenance cost could interrupt development and the demo |
| C: Use private networking | Removes direct public access to the database | Requires private endpoints, network configuration, and a connected development path | The capstone environment does not provide that network setup |

## Consequences

- **Positive:** Every teammate can connect without requesting firewall changes. Rotating home IP addresses do not interrupt development. The shared database remains available for the demo.
- **Negative / follow-ups:** The database endpoint is reachable from the internet. Leaked credentials, connection floods, and gateway vulnerabilities remain residual risks. The rule must be narrowed or removed if the database stores real data.
- **NFR impact:** TLS 1.2 protects data in transit. A strong database password protects authentication. The accepted exposure reduces network isolation.
- **Evidence later labs will need:** The risk register must identify the owner and acceptance date. The defense review must confirm the data remains synthetic. The firewall rule must be removed or narrowed when the training period ends.

## Links

- Accepted Risk Register: `docs/risk-register.md`
- Azure Administration Runbook: `docs/azure-admin-runbook.md`
- Azure Authentication Guide: `docs/azure-authentication.md`
- Azure Database Profile: `backend/src/main/resources/application-azure.yml`
- Environment Template: `.env.example`
