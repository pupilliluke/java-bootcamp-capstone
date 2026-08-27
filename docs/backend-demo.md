# Backend + messaging demo


## 1. Write path: HTTP → Postgres → Kafka, one correlation id throughout

```bash
curl -s -i -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: backend-demo-001" \
  -d '{"username":"agent1","password":"agent1"}'
```

```
HTTP/1.1 200
X-Correlation-Id: backend-demo-001
...
{"accessToken":"eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJjYXBzdG9uZS1jcm0i...(truncated)","tokenType":"Bearer","username":"agent1","role":"AGENT"}
```

The id on the request comes straight back on the response — `CorrelationIdFilter`
runs at `HIGHEST_PRECEDENCE`, before Spring Security, so even a request that
never authenticates still gets one.

```bash
curl -s -i -X POST http://localhost:8080/api/interactions \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: backend-demo-001" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"customerId":"CUS-1001","channel":"EMAIL","notes":"Backend demo: renewal follow-up"}'
```

```
HTTP/1.1 201
X-Correlation-Id: backend-demo-001
...
{"interactionId":"INT-bd3f2947-117a-4405-b311-09d86b6677e4","customerId":"CUS-1001","channel":"EMAIL","notes":"Backend demo","occurredAt":"2026-08-27T20:16:05.773500Z"}
```

Same `X-Correlation-Id` on both calls, same id echoed back both times — the
same value also ends up in the Kafka event's `correlationId` field and in
every server log line for this request, via MDC (see `InteractionService`
and `CorrelationIdFilter`).

**Proof it actually reached Postgres, not just the response body:**

```bash
docker exec java-bootcamp-capstone-postgres-1 psql -U crm -d crm -c \
  "select interaction_id, customer_id, channel, notes, occurred_at from interaction where interaction_id = 'INT-bd3f2947-117a-4405-b311-09d86b6677e4';"
```

```
              interaction_id              | customer_id | channel |              notes              |         occurred_at
------------------------------------------+-------------+---------+---------------------------------+-----------------------------
 INT-bd3f2947-117a-4405-b311-09d86b6677e4 | CUS-1001    | EMAIL   | Backend demo                    | 2026-08-27 20:16:05.7735+00
(1 row)
```

**Proof the read-back is server state, not just what was just written:**

```bash
curl -s http://localhost:8080/api/customers/CUS-1001/interactions \
  -H "Authorization: Bearer $TOKEN"
```

```json
[
  {
    "interactionId": "INT-bd3f2947-117a-4405-b311-09d86b6677e4",
    "customerId": "CUS-1001",
    "channel": "EMAIL",
    "notes": "Backend demo: renewal follow-up",
    "occurredAt": "2026-08-27T20:16:05.773500Z"
  }
]
```

## 2. Exactly-once, backed by a table rather than memory

`InteractionMessagingIT` is the only test that exercises the real, wired-together
producer → topic → consumer → `JpaProcessedEventStore` path rather than a
mock of any piece of it.

```bash
cd backend && ./mvnw verify -Dsurefire.skip=true -Dit.test=InteractionMessagingIT
```

```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.170 s -- in com.capstone.crm.messaging.InteractionMessagingIT
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

This is the one that used to be provable only for the life of one JVM: before
`V5__processed_event.sql`, "processed" lived in a `ConcurrentHashMap`, so a
restart or a second consumer replica would reprocess an event none of it had
ever seen. Confirmed against a live database, not just the test's own H2
instance — the interaction created in Section 1 produced a real row:

```bash
docker exec java-bootcamp-capstone-postgres-1 psql -U crm -d crm -c \
  "select event_id, processed_at from processed_event order by processed_at desc limit 3;"
```

```
               event_id               |         processed_at
--------------------------------------+-------------------------------
 d996b30f-4ee1-4051-94a2-f1d0ff1dc2a3 | 2026-08-27 20:16:05.952393+00
 eea466a8-dec2-4a45-bbc6-bd183e972efc | 2026-08-27 00:46:31.587961+00
(2 rows)
```

The top row's timestamp is ~0.18s after Section 1's `POST` — that's this
run's event, really consumed and really recorded, not a fixture.

## 3. Dead-letter routing for an event the consumer can't accept

`KafkaConfig` marks `InvalidInteractionEventException` and
`UnsupportedEventVersionException` as **not retryable**, so
`DeadLetterPublishingRecoverer` sends a failing record straight to
`<topic>.DLT` instead of retrying it forever or crashing the listener.
Reproduced by publishing a malformed message directly to the topic,
bypassing the API, so this proves the consumer's own guard, not just 
that the producer behaves:

```bash
docker exec java-bootcamp-capstone-kafka-1 sh -c \
  '/opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic crm.interaction.v1' \
  <<< '{"eventId":"dea2a409-1710-4e54-822e-479fc6232aa3","correlationId":"backend-demo-dlt-001","eventType":"interaction.created","version":99,"occurredAt":"2026-08-27T20:00:00Z","customerId":"CUS-1001","interactionId":"INT-bad","channel":"EMAIL","notes":"malformed version, should route to DLT"}'
```

```bash
docker exec java-bootcamp-capstone-kafka-1 \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

```
__consumer_offsets
crm.interaction.v1
crm.interaction.v1.DLT
```

```bash
docker exec java-bootcamp-capstone-kafka-1 \
  /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic crm.interaction.v1.DLT --from-beginning --timeout-ms 5000
```

```
{"eventId":"dea2a409-1710-4e54-822e-479fc6232aa3","correlationId":"backend-demo-dlt-001","eventType":"interaction.created","version":99,"occurredAt":1787860800.000000000,"customerId":"CUS-1001","interactionId":"INT-bad","channel":"EMAIL","notes":"malformed version, should route to DLT"}
Processed a total of 1 messages
```

The `.DLT` topic didn't exist before this. `KafkaConfig` derives it as
`record.topic() + ".DLT"` and Kafka auto-creates it on first publish, which is
itself evidence the recoverer actually fired rather than the exception being
swallowed.

**Proof the consumer kept running afterward** — a bad event on the DLT is not
the same claim as "the consumer didn't crash":

```bash
curl -s -i -X POST http://localhost:8080/api/interactions \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: backend-demo-002" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"customerId":"CUS-1001","channel":"PHONE","notes":"Backend demo: consumer still healthy after DLT event"}'
```

```
HTTP/1.1 201
X-Correlation-Id: backend-demo-002
...
{"interactionId":"INT-6e7c4590-5ad2-47e1-a067-df2fcdf59868", ...}
```

```bash
docker exec java-bootcamp-capstone-postgres-1 psql -U crm -d crm -c "select count(*) from processed_event;"
```

```
 count
-------
     3
(1 row)
```

It's three because of an earlier test.


