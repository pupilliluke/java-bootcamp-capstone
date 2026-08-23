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

Secrets live in a gitignored `.env` at the repository root, never in `application.yml`. Copy the template once and fill it in:

```powershell
Copy-Item .env.example .env
```

`application.yml` imports it with `optional:file:./.env[.properties],optional:file:../.env[.properties]`, so `mvn spring-boot:run` works with nothing exported, whether you launch from the repository root or from `backend/`. Both paths are listed because the import resolves against the JVM working directory, and a path that misses is skipped silently.

`JWT_SECRET` is required and has no default. A committed fallback would be a weak secret that ships, so a missed import crashes the app at startup rather than quietly running on one:

```text
Could not resolve placeholder 'JWT_SECRET' in value "${JWT_SECRET}"
```

HS256 signs with a 256-bit key, so the value must be at least 32 characters. Any string works, and it does **not** have to match what teammates use — it only has to stay stable for you, or previously issued tokens stop verifying.

Tests need none of this: `src/test/resources/application.properties` supplies a test-only value, so `mvn verify` runs with nothing set.

### Deploying

There is no `.env` in a deployed environment. Both imports skip, and the same key names arrive as real environment variables instead — Azure App Service Application Settings, Container Apps secrets, or a Kubernetes Secret. The image is identical on a laptop and in the cloud; only the source of the values changes.

`.env.example` is the committed list of which keys exist. Deploying means copying those keys into the host's settings, never uploading the file.

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
