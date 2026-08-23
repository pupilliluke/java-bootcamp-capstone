# Java Bootcamp Capstone CRM

A Spring Boot customer relationship management backend with Kafka messaging for customer interactions.

## Project status

| Area | Current contents |
| --- | --- |
| Backend | Spring Boot REST API, in-memory customer storage, Kafka producer and consumer |
| Kafka messaging | Versioned interaction events, idempotent consumer processing, retry and dead-letter-topic configuration |
| Tests | Unit tests for publishing and idempotency, plus an embedded Kafka integration test |
| Frontend | Directory reserved for future work |
| Docs, defense, reports | Directories reserved for project material |

## Features

### Customer API

- Create a customer.
- Get one customer by ID.
- List all customers.
- Validate incoming customer requests.
- Return structured errors for missing customers, duplicate customer IDs, and invalid requests.
- Seed two demo customers on startup: `CUS-1001` and `CUS-1002`.

Customer data is stored in memory and resets when the application restarts.

### Kafka interaction messaging

- Accept interaction requests through `POST /api/interactions`.
- Create a Kafka event with a UUID event ID, interaction ID, event type, version, timestamp, customer ID, channel, and notes.
- Publish events to the versioned topic `crm.interaction.v1`.
- Use `customerId` as the Kafka message key to preserve ordering for one customer.
- Consume events with the `crm-interaction-service-v1` consumer group.
- Skip duplicate events with an in-memory processed-event store keyed by `eventId`.
- Retry processing failures twice with a one-second delay.
- Send unrecoverable events to `crm.interaction.v1.DLT`.
- Send invalid events and unsupported event versions directly to the dead-letter topic.
- Log successfully processed interaction events through the current consumer handler.

## Requirements

- Git
- JDK 21
- Maven 3.9 or later
- Docker Desktop with Docker Compose

## Download the project

Clone the repository, then enter its directory:

```powershell
git clone <repository-url>
cd java-bootcamp-capstone
```

Replace `<repository-url>` with the GitHub repository URL.

## Run locally

Start Docker Desktop first. From the repository root, start Kafka:

```powershell
docker compose up -d
```

Start the backend in a second terminal:

```powershell
cd backend
mvn spring-boot:run
```

The API starts at `http://localhost:8080`. Kafka is available at `localhost:9092`.

To stop the local Kafka container:

```powershell
cd ..
docker compose down
```

## API endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/customers` | List customers |
| `GET` | `/api/customers/{customerId}` | Get one customer |
| `POST` | `/api/customers` | Create a customer |
| `POST` | `/api/interactions` | Publish an interaction event |

The interaction endpoint accepts `customerId`, `channel`, and `notes`. It returns `202 Accepted` with the created event.

## Test the project

From the `backend` directory, run unit tests:

```powershell
mvn test
```

Run the complete verification suite:

```powershell
mvn verify
```

`mvn verify` runs the unit tests and the embedded Kafka integration test. The integration test starts its own temporary Kafka broker, so it does not require the Docker Kafka container.

For the final pre-push check:

```powershell
mvn clean verify
```

## Configuration

### Kafka broker

The backend uses `localhost:9092` by default. Override the broker address with the `KAFKA_BOOTSTRAP_SERVERS` environment variable when needed.

```powershell
$env:KAFKA_BOOTSTRAP_SERVERS = "localhost:9092"
mvn spring-boot:run
```

### JWT secret

`JWT_SECRET` is required and has no default. A fallback committed to the repository would be a weak secret that ships, so the application refuses to start without it:

```text
Could not resolve placeholder 'JWT_SECRET' in value "${JWT_SECRET}"
```

Set it before running the backend:

```powershell
$env:JWT_SECRET = "local-dev-secret-at-least-32-bytes-long!"
mvn spring-boot:run
```

HS256 signs with a 256-bit key, so the value must be at least 32 characters. A shorter one also fails at startup rather than producing weakly signed tokens.

Tests do not need it: `src/test/resources/application.properties` supplies a test-only value, so `mvn verify` runs with the variable unset.

### Demo accounts

Authentication uses two in-memory accounts. `POST /api/auth/login` returns a bearer token to send as `Authorization: Bearer <token>`.

| Username | Password | Role |
| -------- | -------- | ---- |
| `agent1` | `agent1` | AGENT |
| `admin1` | `admin1` | ADMIN |

`/api/customers` and `/api/interactions` require AGENT or ADMIN. `/api/admin` requires ADMIN. `/api/auth/login` and the Actuator health probes are public.

## Project structure

```text
java-bootcamp-capstone/
├── backend/                 Spring Boot application and tests
├── frontend/                React + TypeScript application (Vite)
├── docs/                    Reserved for documentation
├── defense/                 Reserved for defense material
├── reports/                 Reserved for reports
├── docker-compose.yml       Local Kafka service
└── README.md
```
