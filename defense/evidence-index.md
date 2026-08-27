# Evidence index (starter)

| Claim             | Artifact path                                                                    | Scrubbed? |
|-------------------|----------------------------------------------------------------------------------|-----------|
| Product outcome   | `docs/architecture/context.md`                                                   | Yes       |
| Container view    | `docs/architecture/container.md`                                                 | Yes       |
| ADR — DB          | `docs/adrs/ADR-009-postgresql.md`                                                | Yes       |
| ADR — Kafka       | `docs/adrs/ADR-010-kafka-event-contract.md`                                      | Yes       |
| Backend demo      | `docs/backend-demo.md`                                                           | Yes       |
| Verify log        | `https://github.com/pupilliluke/java-bootcamp-capstone/actions/runs/33010455301` | Yes       |
| UI→DB proof       | `docs/frontend-persistence-demo.md` / SQL shot                                   | Yes       |
| Migration         | `backend/target/classes/db/migration/V…sql`                                      | Yes       |
| Pipeline / digest | `docs/security-deploy-demo.md`                                                   | Yes       |
| Rollback          | `docs/rollback-runbook.md` `screenshots/rollback/`                               | Yes       |
| Deny 401/403      | `screenshots/401_evidence.png` `screenshots/403_evidence.png`                    | Yes       |

**Our RBAC prevents any 403s because the actions that could cause a 403 are disabled. I can trigger it from the terminal**