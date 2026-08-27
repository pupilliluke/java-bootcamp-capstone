# Architecture

What the system is today, not what it is planned to become. Anything not yet
built is listed under [Gaps](#gaps) rather than drawn as if it exists.

Start with the [system context view](architecture/context.md) for the CRM as one
box, its external participants, and its trust boundaries. The views below open
that box to show its containers, request paths, messaging, persistence, and
deployment.

## Container view

```mermaid
%%{init: {"flowchart": {"curve": "linear", "nodeSpacing": 45, "rankSpacing": 55}}}%%
flowchart TB
  Agent["Service agent"]

  subgraph FE["Frontend — React 18 + TypeScript, Vite :5173"]
    direction TB
    Guard["ProtectedRoute gate"]
    Work["CustomerWorkspace"]
    Client["http.ts and tokenStore"]
  end

  subgraph BE["Backend — Spring Boot 3.3.5, Java 21, :8080"]
    direction TB
    Filter["JwtAuthenticationFilter and SecurityConfig"]
    Ctrl["AuthController, CustomerController, InteractionController"]
    Svc["CustomerService, InteractionService, JwtService"]
    Repo["AppUserRepository via JPA, CustomerRepository in memory"]
  end

  DB[("PostgreSQL — app_user")]
  Topic[["Kafka — crm.interaction.v1"]]

  Agent --> Guard
  Guard --> Work
  Work --> Client
  Client -- "REST with bearer token" --> Filter
  Filter --> Ctrl
  Ctrl --> Svc
  Svc --> Repo
  Repo --> DB
  Svc --> Topic
```

## Request paths

| Path | Public | AGENT | ADMIN |
| ---- | ------ | ----- | ----- |
| `/api/v1/auth/login` | ✅ | ✅ | ✅ |
| `/actuator/health` and probes | ✅ | ✅ | ✅ |
| `/api/v1/customers/**` | ❌ | ✅ | ✅ |
| `/api/v1/interactions/**` | ❌ | ✅ | ✅ |
| `/api/v1/admin/**` | ❌ | ❌ 403 | ✅ |
| anything else | ❌ | authenticated | authenticated |

## Authentication flow

```mermaid
sequenceDiagram
  autonumber
  participant UI as React UI
  participant API as AuthController
  participant DB as PostgreSQL
  participant JWT as JwtService

  UI->>API: POST /api/v1/auth/login
  API->>DB: findByUsername
  DB-->>API: app_user row with BCrypt hash and role
  API->>API: passwordEncoder.matches
  API->>JWT: issueToken(username, role)
  JWT-->>API: HS256 token, expires in 60 min
  API-->>UI: accessToken, tokenType, username, role
  Note over UI: token kept in memory only
  UI->>API: later calls send Authorization Bearer
```

Unknown username and wrong password return the same 401, so the endpoint cannot
be used to enumerate accounts. A federated account has a null `password_hash`,
which can never satisfy a BCrypt check.

## Where the local database actually lives

It is a Docker container, not a file in the repository and not anything in the
browser. Nothing about it touches `localStorage` — that is browser storage, a
different thing entirely, and the only thing the app keeps there is nothing at
all (the JWT is held in memory).

```mermaid
%%{init: {"flowchart": {"curve": "linear", "rankSpacing": 70}}}%%
flowchart LR
  App["Spring Boot on the Windows host"]

  subgraph Docker["Docker Desktop — WSL2 Linux VM"]
    direction TB
    PG["postgres:17 container, listening on 5432"]
    Vol[("named volume crm_pgdata")]
  end

  App -- "JDBC to localhost:5432" --> PG
  PG -- "/var/lib/postgresql/data" --> Vol
```

The volume's reported mountpoint is
`/var/lib/docker/volumes/java-bootcamp-capstone_crm_pgdata/_data`. That is a path
*inside the Linux VM*, not a Windows directory — the real bytes sit in the WSL2
virtual disk that Docker Desktop manages.

Consequences worth knowing:

| Action | Data |
| ------ | ---- |
| `docker compose stop` / restart | kept |
| `docker compose down` | kept — the volume outlives the container |
| `docker compose down -v` | **destroyed** — the volume is deleted |
| Deleting the repository | kept — the volume is not in the working tree |
| `mvn test` | untouched — tests run on in-memory H2 |

The container currently holds `app_user`, `interaction`, and
`flyway_schema_history`. Flyway creates and tracks them, so a wiped volume rebuilds itself on the next
startup and the seeder re-adds `agent1` and `admin1`.

## Messaging: today and the target

### Today

The synchronous path now validates the customer, saves the interaction, and
publishes its event. A nested GET reads the durable row back for the customer.
The remaining messaging gaps are the outbox and a durable consumer side effect.

```mermaid
%%{init: {"flowchart": {"curve": "linear", "rankSpacing": 50, "nodeSpacing": 40}}}%%
flowchart TB
  Ctrl["InteractionController, POST /api/v1/interactions"]
  Read["GET /api/v1/customers/{id}/interactions"]
  Svc["InteractionService.createAndPublish"]
  Cust["CustomerService, in-memory map, validation only"]
  IntT[("interaction table")]
  Prod["InteractionEventProducer"]
  Topic[["topic crm.interaction.v1"]]
  Cons["InteractionEventConsumer"]
  Seen["InMemoryProcessedEventStore, a ConcurrentHashMap"]
  Handler["LoggingInteractionEventHandler"]
  Out["a log line, and nothing else"]
  DLT[["topic crm.interaction.v1.DLT"]]

  Ctrl --> Svc
  Svc -- "customer exists?" --> Cust
  Svc -- "insert before publish" --> IntT
  Read --> IntT
  Svc --> Prod
  Prod --> Topic
  Topic --> Cons
  Cons -- "seen before?" --> Seen
  Cons --> Handler
  Handler --> Out
  Cons -- "invalid or unsupported version" --> DLT
```

### Target

The first high-value change—writing the interaction inside a transaction before
publishing—is complete. The target still adds an outbox and gives the consumer
a durable side effect so its idempotency guarantee protects something real.

```mermaid
%%{init: {"flowchart": {"curve": "linear", "rankSpacing": 50, "nodeSpacing": 40}}}%%
flowchart TB
  Ctrl["InteractionController"]
  Svc["InteractionService, @Transactional"]
  CustT[("customer table")]
  IntT[("interaction table")]
  OutT[("outbox table")]
  Relay["outbox relay, publishes committed rows"]
  Topic[["topic crm.interaction.v1"]]
  Cons["InteractionEventConsumer"]
  SeenT[("processed_event table")]
  Handler["audit handler"]
  AuditT[("interaction_audit table")]
  DLT[["topic crm.interaction.v1.DLT"]]

  Ctrl --> Svc
  Svc -- "customer exists?" --> CustT
  Svc -- "insert interaction" --> IntT
  Svc -- "insert event, same transaction" --> OutT
  OutT --> Relay
  Relay --> Topic
  Topic --> Cons
  Cons -- "claim event id" --> SeenT
  Cons --> Handler
  Handler --> AuditT
  Cons -- "poison message" --> DLT
```

### What changes, and why

| | Today | Target | Why |
| ---- | ----- | ------ | --- |
| Interaction record | row in `interaction`, written before publishing and exposed by GET | keep this behavior | The create endpoint returns `201 Created`, and the browser journey independently verifies persistence by reading the interaction back. |
| Customer lookup | in-memory map | `customer` table | Survives a restart. Also what Lab 50's UI-to-database flow needs. |
| Publish | directly from the service | outbox row, published by a relay | Writing the row and publishing separately is a dual write: the transaction can commit and the publish fail, leaving the database and the log disagreeing. One transaction removes the gap. |
| Idempotency store | `ConcurrentHashMap` | `processed_event` table | Per-JVM state is per-replica state. With more than one pod, each has its own set, and the exactly-once guarantee stops holding. |
| Consumer effect | a log line | a durable audit row | Reprocessing a log statement costs nothing, so idempotency currently protects nothing. Give the consumer a real side effect and the guarantee starts to matter. |

The outbox is the part worth naming in the defense even if it is not built. When
a sponsor asks "what happens if Kafka is down when you save?", the honest answer
today is that the save succeeds and the event is lost. The outbox is why that
question has a good answer.

An intermediate step is legitimate: write the interaction row and publish
directly, accepting the dual write and documenting it as residual risk. That
alone closes the persistence gap, which is the part that is scored.

## Deployment

The container view above is deliberately environment-agnostic. This is where the
same containers get mapped onto machines.

Only the database has moved to Azure. The API, the UI and Kafka all still run on
a developer laptop, so there is no deployed application yet — just a hosted
database that a local application can point at.

```mermaid
%%{init: {"flowchart": {"curve": "linear", "rankSpacing": 70, "nodeSpacing": 45}}}%%
flowchart LR
  subgraph Laptop["Developer laptop"]
    direction TB
    UI["Vite dev server :5173"]
    Api["Spring Boot :8080"]
    Kafka["Kafka in Docker :9092"]
    LocalPG["postgres:17 in Docker :5432"]
  end

  subgraph Azure["Microsoft Azure"]
    AzurePG[("Azure Database for PostgreSQL Flexible Server, version 18, TLS required")]
  end

  UI --> Api
  Api --> Kafka
  Api -- "default when DB_URL is unset" --> LocalPG
  Api -- "when DB_URL is set in .env" --> AzurePG
```

Which database is used is decided entirely by `.env`. Nothing in the committed
configuration names the Azure server, and the same build runs against either.

| | Local | Azure |
| ---- | ----- | ----- |
| Chosen by | defaults in `application.yml` | `DB_URL`, `DB_USER`, `DB_PASSWORD` in `.env` |
| PostgreSQL version | 17 | 18 |
| Transport | plaintext on localhost | TLS 1.2, `sslmode=require` |
| Schema created by | Flyway on first start | Flyway on first start |
| Used by tests | no — tests use H2, except `AppUserRepositoryIT` which pins localhost | never |

## Persistence

`app_user` is the only table so far. `password_hash` is nullable and `email` is
present from the first migration, so an external identity provider slots in
without a schema change.

| Column | Notes |
| ------ | ----- |
| `username` | unique, business key |
| `email` | unique, indexed — lookup key for federated sign-in |
| `password_hash` | BCrypt; null for federated accounts |
| `role` | `AGENT` or `ADMIN`, enforced by a check constraint |
| `enabled` | disables an account without deleting it |

The migration is deliberately portable SQL. It runs against PostgreSQL in
normal operation and against H2 in PostgreSQL mode under test, so the schema the
tests exercise is the schema that ships.

## Gaps

Not yet built, listed so the diagram is not read as a plan:

- **Customers are still in memory.** `CustomerRepository` is a
  `ConcurrentHashMap`, so interaction rows use an indexed customer business key
  until the customer table exists and can provide a foreign key.
- **The publish is still a dual write.** The interaction row and direct Kafka
  send are not one atomic resource; the outbox shown above remains the target.
- **Consumer idempotency is still in memory.** It protects a log statement, not
  a durable audit row.
- **Nothing deploys from the pipeline yet.** The container image is built and
  checked on every pull request — `backend/Dockerfile`, hadolint,
  container-structure-test, trivy, and `ContainerImageIT` — but no job publishes
  it to a registry or applies it to a cluster.
