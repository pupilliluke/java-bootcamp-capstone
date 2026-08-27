# Frontend / persistence durability demo

Lab 50's signature proof: an interaction recorded through the app survives a
backend restart, found again by the **correlation id** that made it. This is the
evidence for issue #88 — the `correlation_id` column added in
`V8__interaction_audit_columns.sql`, which is what makes the SELECT below
possible at all.

## The claim

A `POST /api/interactions` writes a durable row carrying the request's
correlation id. Stop and restart the API — a different process against the same
database — and the same row is still there, retrievable by `customer_id` +
`correlation_id`. Nothing about the row lives in the application's memory.

## Rehearsal, 2026-08-27

Against a real PostgreSQL (an isolated `crm_i86` database, so the shared local
schema is untouched), the digest-built backend jar run twice with a stop in
between.

### 1. Create — as `agent1`, correlation id `durability-proof-001`

```
POST /api/interactions
X-Correlation-Id: durability-proof-001
{"customerId":"CUS-1001","channel":"PHONE","notes":"Durability rehearsal for issue #88."}

-> 201 Created
{"interactionId":"INT-79701bd5-819a-4c38-ad80-86c6760000ef", ...}
```

### 2. SELECT by customer + correlation id — before restart

```sql
SELECT interaction_id, customer_id, correlation_id, created_by, channel, occurred_at
FROM interaction
WHERE customer_id = 'CUS-1001' AND correlation_id = 'durability-proof-001';
```

```
interaction_id | INT-79701bd5-819a-4c38-ad80-86c6760000ef
customer_id    | CUS-1001
correlation_id | durability-proof-001
created_by     | agent1
channel        | PHONE
occurred_at    | 2026-08-27 18:48:10.589586+00
```

### 3. Restart the backend

The JVM is stopped and a fresh one started against the same database — a real
process restart, not a transaction rollback. Any state held only in memory is
gone at this point.

### 4. The same SELECT — after restart

```
interaction_id | INT-79701bd5-819a-4c38-ad80-86c6760000ef   <- same row
customer_id    | CUS-1001
correlation_id | durability-proof-001
created_by     | agent1
channel        | PHONE
```

And through the API on the restarted instance:

```
GET /api/customers/CUS-1001/interactions
-> "interactionId":"INT-79701bd5-819a-4c38-ad80-86c6760000ef"
```

## What this proves

- **Durable, not in-memory.** The identical `interaction_id` returns from a
  process that did not exist when the row was written. Persistence is the
  database's, not the application's.
- **The correlation id reaches the row (issue #88).** The SELECT keys on
  `correlation_id`; before `V8` that column did not exist and this query could
  not be written. The id now survives request → log → event → **row**.
- **The actor is recorded (issue #86).** `created_by = agent1` is the
  authenticated user captured at create time — the audit trail the event also
  carries.

## What it does not prove

- Not a volume or crash-consistency test — one row, a clean stop/start, a single
  node. It demonstrates the durability contract, not database HA.
- The rehearsal used an isolated `crm_i86` database created for the run and
  dropped after; the row above does not persist in any shared environment.
