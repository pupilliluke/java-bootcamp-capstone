# Architecture

What the system is today, not what it is planned to become. Anything not yet
built is listed under [Gaps](#gaps) rather than drawn as if it exists.

## Container view

```mermaid
flowchart TB
  Agent(["Service agent<br/>(browser)"])

  subgraph Frontend["Frontend · React 18 + TypeScript · Vite :5173"]
    Guard["ProtectedRoute<br/>login gate"]
    Workspace["CustomerWorkspace<br/>search → profile → interaction"]
    Store["tokenStore<br/>JWT held in memory"]
    Http["http.ts<br/>attaches bearer, clears session on 401"]
  end

  subgraph Backend["Backend · Spring Boot 3.3.5 · Java 21 · :8080"]
    Filter["JwtAuthenticationFilter<br/>+ SecurityConfig RBAC"]
    AuthC["AuthController<br/>/api/auth/login"]
    CustC["CustomerController<br/>/api/customers"]
    IntC["InteractionController<br/>/api/interactions"]
    Jwt["JwtService<br/>HS256, iss/sub/role/iat/exp"]
    Users["CrmUserDetailsService<br/>+ AppUserRepository"]
    CustSvc["CustomerService<br/>in-memory store"]
    IntSvc["InteractionService"]
    Prod["InteractionEventProducer"]
    Cons["InteractionEventConsumer"]
  end

  DB[("PostgreSQL 17<br/>app_user · Flyway V1")]
  Topic[["Kafka 4.3.1<br/>crm.interaction.v1"]]

  Agent --> Guard
  Guard --> Workspace
  Workspace --> Http
  Http <--> Store
  Http -->|"HTTPS / REST<br/>Authorization: Bearer"| Filter

  Filter --> AuthC
  Filter --> CustC
  Filter --> IntC
  Filter -.->|"verify signature,<br/>issuer, exp"| Jwt

  AuthC --> Users
  AuthC -->|"issue token"| Jwt
  Users -->|"JPA"| DB

  CustC --> CustSvc
  IntC --> IntSvc
  IntSvc --> Prod
  Prod -->|publish| Topic
  Topic -->|consume| Cons
```

## Request paths

| Path | Public | AGENT | ADMIN |
| ---- | ------ | ----- | ----- |
| `/api/auth/login` | ✅ | ✅ | ✅ |
| `/actuator/health` and probes | ✅ | ✅ | ✅ |
| `/api/customers/**` | ❌ | ✅ | ✅ |
| `/api/interactions/**` | ❌ | ✅ | ✅ |
| `/api/admin/**` | ❌ | ❌ 403 | ✅ |
| anything else | ❌ | authenticated | authenticated |

## Authentication flow

```mermaid
sequenceDiagram
  participant UI as React UI
  participant API as AuthController
  participant DB as PostgreSQL
  participant JWT as JwtService

  UI->>API: POST /api/auth/login {username, password}
  API->>DB: findByUsername
  DB-->>API: app_user row (BCrypt hash, role)
  API->>API: passwordEncoder.matches
  API->>JWT: issueToken(username, role)
  JWT-->>API: HS256 token, exp 60 min
  API-->>UI: {accessToken, tokenType, username, role}
  Note over UI: token kept in memory only,<br/>a refresh signs the user out
  UI->>API: subsequent calls with Authorization: Bearer
```

Unknown username and wrong password return the same 401, so the endpoint cannot
be used to enumerate accounts. A federated account has a null `password_hash`,
which can never satisfy a BCrypt check.

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

- **Customers and interactions are still in memory.** `CustomerRepository` is a
  `ConcurrentHashMap` and holds a TODO to become a JPA repository. Only
  authentication is persisted.
- **No frontend route for interactions reaches the backend.** The UI posts to
  `/api/customers/{id}/interactions` with `{channel, summary}`; the API serves
  `/api/interactions` with `{customerId, channel, notes}`. Every attempt 404s.
- **No `/api/admin` handler.** The RBAC rule exists and is tested, but nothing is
  mapped beneath it.
- **No CI pipeline, container image, or Kubernetes manifests.** `k8s/` and
  `infra/` do not exist yet.
- **A missing route returns 500, not 404.** The catch-all
  `@ExceptionHandler(Exception.class)` swallows Spring's `NoResourceFoundException`
  and reports it as a server error, leaking the requested path.
