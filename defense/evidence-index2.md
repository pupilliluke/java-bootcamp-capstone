# Evidence index (starter)

Every slide claim must point here. Paths relative to `customer-management-platform/` unless noted.

| Claim             | Artifact path                                                                     | Scrubbed? |
|-------------------|-----------------------------------------------------------------------------------|-----------|
| Product outcome   | `docs/architecture/context.md`                                                    | Y/N       |
| Container view    | `docs/architecture/container.md`                                                  | Y/N       |
| ADR — DB          | `docs/adrs/ADR-009-postgresql.md`                                                 |  Y/N      |
| ADR — Kafka       | `docs/adrs/ADR-010-kafka-event-contract.md`                                       | Y/N       |
| Backend demo      | `docs/backend-demo.md`                                                            | Y/N       |
| Verify log        | `https://github.com/pupilliluke/java-bootcamp-capstone/actions/runs/33010455301`  | Y/N       |
| UI→DB proof       | `docs/frontend-persistence-demo.md` / SQL shot                                    | Y/N       |
| Migration         | `backend/target/classes/db/migration/V…sql`                                       | Y/N       |
| Pipeline / digest | `docs/security-deploy-demo.md`                                                    | Y/N       |
| Rollback          | `docs/rollback-runbook.md` ADD SCREENSHOTS                                        | Y/N       |
| Deny 401/403      | `screenshots/401_evidence.png` `screenshots/403_evidence.png`                     | Y/N       |
**Our RBAC prevents any 403s because the actions that could cause a 403 are disabled. I can trigger it from the terminal**