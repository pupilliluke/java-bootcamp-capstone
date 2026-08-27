Technical Q&A — prepared answers
TODO: Fill claim → evidence → trade-off → next-step answers before the panel.

Why PostgreSQL?
TODO

Why Kafka after persist?
TODO

What if the panel asks for the JWT?
TODO — never paste live tokens into slides.

How do you prove the same artifact reached staging?
TODO — cite digest / artifact-manifest.json / Lab 43 SHA256SUMS.

What fails closed?
TODO — 401 / 403 / readiness

What does the interaction event carry, and what do you NOT claim about it?
The event carries the interaction fields, the correlation id, and — added in
issue #86 — the acting user (`actor`). It also carries the full `notes` text.
Non-claim, stated plainly: we do **not** claim note contents are kept off the
broker. They are on the topic by decision (ADR-010). The mitigation is that the
topic is namespace-prefixed (`studentNN.crm.interaction.v1`), so the audience is
our own consumers rather than the whole cohort, and the consumer log line
redacts notes — so notes are on the topic but not in the logs. The audit trail
(actor + correlation id) is recorded on the row (`created_by`, `correlation_id`,
V8), which is where the CAP-12 "who did what, correlated" question is answered.