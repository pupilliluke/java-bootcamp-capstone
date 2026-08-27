# ADR-010: One versioned interaction topic, keyed by customer

- **Status:** Accepted
- **Date:** 2026-08-27 (records a decision already implemented; transcribed from the producer, `KafkaConfig`, and the `crm.messaging` block in `application.yml`)
- **Deciders:** Team
- **Related backlog:** Issue #32

## Context

Recording an interaction must emit an event for downstream processing and audit. The course broker is one shared Kafka for the whole cohort with no per-team isolation, and every team is building this same CRM — a hardcoded topic name means four teams publishing into one stream and quietly reading each other's data, which does not fail, it just produces wrong answers. Consumers must survive duplicates and poison messages, and the contract has to be able to change without breaking a consumer that has not caught up.

## Decision

We will publish interaction events to a single versioned topic, `crm.interaction.v1`, with the name injected from configuration (`crm.messaging.interaction-topic`) so a shared cluster gets a namespace prefix (`studentNN.crm.interaction.v1`) — the consumer group is namespaced the same way, because the group id is a separate broker-side namespace that a topic prefix alone does not fix.

The payload is JSON: an `InteractionEvent` record carrying `eventId`, `correlationId`, `eventType`, an explicit integer `version` (1), `occurredAt`, and the interaction fields. The message key is `customerId`, so one customer's events stay ordered. The dead letter topic is derived, `record.topic() + ".DLT"`, so a prefixed topic gets a prefixed DLT with no second value to keep in step. The producer is idempotent with `acks=all`; the consumer validates `version`, deduplicates on `eventId`, retries with a bounded backoff, and parks what still fails on the DLT.

**Compatibility rule:** additive, optional fields may join v1 — a v1 consumer that ignores unknown fields keeps working. Any change a v1 consumer would misread (renaming, retyping, or removing a field, or changing the key) bumps the `version` field *and* the topic suffix to `.v2`, consumed in parallel until v1 drains. Nothing mutates the meaning of v1 in place.

## Alternatives considered

| Option | Pros | Cons | Why not |
| ------ | ---- | ---- | ------- |
| A: Avro or Protobuf with a schema registry | Compatibility enforced by tooling, not discipline | The course broker has no registry; adds infrastructure a one-week delivery cannot carry | JSON with an explicit version field buys the traceability at a cost we can afford |
| B: Hardcoded topic constant | One less property to configure | Collides silently on the shared cohort broker — the worst failure mode is wrong answers, not errors | The property's default keeps local runs zero-config while the prefix stays possible |
| C: Key by `eventId` | Perfectly even partition spread | Loses per-customer ordering, which the interaction timeline read depends on | Ordering is worth more than spread at this scale |

## Consequences

- **Positive:** Every event is traceable (`correlationId`) and replay-safe (`earliest` offset reset plus `eventId` dedupe); poison messages are parked, named, and inspectable on the DLT; two teams on one broker cannot cross streams if both prefix.
- **Negative / follow-ups:** The dedupe store is in memory today, so a restart forgets what it has processed — durable dedupe is an open follow-up. The event currently carries the full `notes` text and no actor, which is an undecided contract question — issue #86. JSON means the compatibility rule is enforced by review, not by tooling.
- **NFR impact:** Traceability (NFR-3 — the correlation id must reach the event) and recoverability (replay without double side effects).
- **Evidence later labs will need:** `InteractionMessagingIT` (publish, consume, duplicate, invalid, DLT), producer and consumer unit tests, and one captured `kafka-console-consumer` record showing `lab-request-001`.

## Links

- Topic and group configuration: `backend/src/main/resources/application.yml` (`crm.messaging`)
- Producer: `backend/src/main/java/com/capstone/crm/messaging/producer/InteractionEventProducer.java`
- Consumer and dedupe: `backend/src/main/java/com/capstone/crm/messaging/consumer/`
- Error handling and DLT: `backend/src/main/java/com/capstone/crm/config/KafkaConfig.java`
- Contract: `backend/src/main/java/com/capstone/crm/messaging/event/InteractionEvent.java`
- Save-then-publish ordering: `docs/adrs/ADR-008-interaction-dual-write.md`
- Open contract question: issue #86
