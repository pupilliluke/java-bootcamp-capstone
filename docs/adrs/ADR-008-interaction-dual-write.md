# ADR-008: Save the interaction and publish its event as a dual write

- **Status:** Accepted
- **Date:** 08-25-2026
- **Deciders:** Luke Pupilli
- **Related backlog:** Issue #22

## Context

`InteractionService.createAndPublish` writes a row to `interaction` and sends an `InteractionEvent` to `crm.interaction.v1`. These are two different systems with two different failure modes, and no transaction spans both. PostgreSQL can roll back; Kafka cannot.

The method is annotated `@Transactional`, which is worth being precise about because it is easy to read as a guarantee it does not give. The transaction covers the database work only. `producer.publish(event)` is called inside the transactional method, so the ordering is:

1. `interactionRepository.save(...)` stages an insert
2. `producer.publish(event)` sends to Kafka
3. the method returns and the transaction commits

**The event is published before the row is committed.** Issue #22 describes the risk the other way round — "the row can commit while the event is lost" — and that is not what this code does. The exposure is a consumer receiving `interaction.created`, reading back through `GET /api/customers/{id}/interactions`, and finding nothing, because the insert has not committed yet. If the commit then fails, the event describes an interaction that will never exist.

This is not hypothetical in the way "database outage" is hypothetical. The read-back path exists and the browser journey exercises it.

## Decision

We will keep the dual write for now, and record it here rather than describe the system as transactional end to end.

We will not reorder the publish into an `afterCommit` callback in this change. That swaps one failure for another — the row commits and the send fails, leaving a durable interaction no consumer hears about — and choosing between them is a decision about which side is allowed to be wrong, not a bug fix.

## Alternatives considered

| Option | Pros | Cons | Why not |
| ------ | ---- | ---- | ------- |
| A: Publish inside the transaction (current) | Simple; a failed publish rolls back the row | Event reaches consumers before the row is visible, and can describe a row that never commits | Selected for now, with this ADR as the record |
| B: Publish from `TransactionSynchronization.afterCommit` | Consumers only ever see events whose row is committed and readable | A failed send after a successful commit loses the event silently | Better ordering, but still a dual write; worth doing, not sufficient |
| C: Transactional outbox — insert the event as a row in the same transaction, relay it separately | One atomic write; the relay retries until the broker accepts | Needs an `outbox` table, a relay, and its own delivery semantics | The correct answer, and more work than this issue |
| D: Kafka transactions spanning both | Genuinely atomic | Requires XA or an exactly-once processor and adds operational weight | Disproportionate for a demo with one producer |

## Consequences

- **Positive:** The limitation is written down and numbered, so the defence can name it rather than be caught by it. The ordering is stated correctly, which the issue text was not.
- **Negative / follow-ups:** A consumer that reads back immediately can observe an event before its row. `docs/architecture.md` already names the outbox as the target; option C is the work that closes this, and until it lands the consumer only writes a log line, which limits the blast radius.
- **NFR impact:** Bears on the durability and consistency claims. The interaction is durable once committed; the *event* and the row are not guaranteed to agree.

## What protects the invariant today

There is no foreign key on `interaction.customer_id`, so `customerService.get(...)` in `createAndPublish` is the only thing preventing an interaction attached to a customer that does not exist. `InteractionServiceTest.anUnknownCustomerSavesNothingAndPublishesNothing` pins that: an unknown customer must reach neither the repository nor the producer. It was verified by moving the lookup below the save and confirming the test fails.
