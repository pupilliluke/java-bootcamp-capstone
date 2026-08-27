# Java Bootcamp Capstone CRM

A customer relationship management application: a React front end, a Spring Boot
REST API with JWT authentication, Kafka messaging for customer interactions, and
PostgreSQL for accounts.

## Project status

| Area | Current contents |
| --- | --- |
| Front end | React and TypeScript, nine screens behind a login guard |
| Backend | Spring Boot REST API, JWT authentication and roles, Kafka producer and consumer |
| Database | PostgreSQL with Flyway: `app_user`, `customer`, `interaction`, and processed-event rows, all durable |
| Kafka messaging | Versioned interaction events, idempotent consumer processing, retry and dead-letter-topic configuration |
| Tests | Backend/frontend tests, embedded Kafka and real-PostgreSQL integration coverage, plus a Playwright browser journey |
| Docs | Six planning documents in `docs/`, starting with `docs/backlog.md` |
| Defense, reports | Directories reserved for project material |

**What is not built yet:** the contacts, activities, and reports screens still
run on demo data — every panel that does carries a "Demo data" tag.

## Features

### Front end

Everything sits behind a login guard, so signing out unmounts the workspace and
the customer data loaded with it.

- Sign in, sign out, and a sidebar for moving between screens.
- Dashboard, customer list with paging, customer details, and an add-customer form.
- Log an interaction and read its persisted history from the customer details screen.
- Contacts, activities, and reports screens.

Screens navigate through React state rather than a router library, which keeps
the dependency list to React alone. The trade is that there are no URLs: the
browser back button does nothing and a refresh returns you to the dashboard.

Two things are deliberately honest rather than finished:

- **Editing a customer is disabled.** The form opens and shows a message
  explaining that the API has no `PUT /api/v1/customers` yet, and the save button
  stays greyed out.
- **Contacts, activities, and reports run on hardcoded data**, because no
  endpoint serves them. Every panel that does carries a "Demo data" tag so
  fabricated rows are never mistaken for real ones.

The bearer token is held in memory, not `localStorage`, so a script injected
into the page cannot read it. The trade is that a refresh signs you out. Any 401
from the API clears the session and returns to the login screen.

### Customer API

- Create a customer.
- Get one customer by ID.
- List all customers.
- Validate incoming customer requests.
- Return structured errors for missing customers, duplicate customer IDs, and invalid requests.
- Seed two demo customers on startup: `CUS-1001` and `CUS-1002`.

Customer rows are durable in PostgreSQL (`V3__customer.sql`); the seeder skips
any customer that already exists, so restarts neither wipe nor duplicate them.

### Kafka interaction messaging

- Accept interaction requests through `POST /api/v1/interactions`.
- Save each accepted interaction before publishing its event.
- Read a customer's interactions through `GET /api/v1/customers/{customerId}/interactions`.
- Create a Kafka event with a UUID event ID, interaction ID, event type, version, timestamp, customer ID, channel, and notes.
- Publish events to the versioned topic named by `CRM_INTERACTION_TOPIC`, `crm.interaction.v1` by default.
- Use `customerId` as the Kafka message key to preserve ordering for one customer.
- Consume events with the group named by `CRM_CONSUMER_GROUP`, `crm-interaction-service-v1` by default.
- Prefix both with your namespace on a shared broker, so teams do not read each other's events.
- Skip duplicate events with an in-memory processed-event store keyed by `eventId`.
- Retry processing failures twice with a one-second delay.
- Send unrecoverable events to the topic's `.DLT`, derived from the topic so a prefix carries over.
- Send invalid events and unsupported event versions directly to the dead-letter topic.
- Log successfully processed interaction events through the current consumer handler.

## Requirements

- Git
- JDK 21
- Node.js 20 or later, for the front end and the setup check
- Docker Desktop with Docker Compose

Maven is not in the list. Use the `mvnw` wrapper committed in `backend/` — it
pins the version everyone builds with, so nobody has to match it by hand.

## Quick setup

Seven steps from clone to a working login, plus two more if you want the shared
Azure database. PowerShell shown; `cmd` differs only at step 2.

### Local (everything on your machine)

1. `git clone <repository-url>` then `cd java-bootcamp-capstone`
2. `Copy-Item .env.example .env` — `cmd`: `copy .env.example .env`
3. Open `.env`, set `JWT_SECRET` to any string of 32 characters or more, and set `LOCAL_DB_PASSWORD` to anything
4. `docker compose up -d` — reads the same `.env` and creates the container with those credentials
5. `cd backend` then `.\mvnw spring-boot:run` — Flyway builds the schema and seeds the demo accounts
6. New terminal: `cd frontend` then `npm ci` then `npm run dev`
7. Open <http://localhost:5173> and sign in as `agent1` / `agent1`

The `local` profile is the default, so nothing needs selecting. `.env` is the
only file you edit, and both Docker Compose and Spring read it — which is why
step 3 comes before step 4.

### Azure (optional, shared database)

8. Get the `AZURE_DB_*` values from a teammate out of band and uncomment them in `.env`
9. Start the backend with the profile: `$env:SPRING_PROFILES_ACTIVE = "azure"` then `.\mvnw spring-boot:run`

Swapping back to local is unsetting the profile. Tests are unaffected either
way: they run under their own `test` profile on in-memory H2 and never touch
Azure.

### Ports

| Service | Port | |
| ------- | ---- | - |
| Front end, Vite | **5173** | the one you open in a browser |
| Backend API | 8080 | the front end talks to it; you rarely visit it directly |
| PostgreSQL | 5432 | started by `docker compose` |
| Kafka | 9092 | started by `docker compose` |

Postgres and Kafka restart with Docker Desktop, so normally only the backend and
the front end need starting by hand.

### Verify your setup

With the backend running, from the repository root. Same command on Windows 10, Windows 11 and macOS — it uses Node, which you already have for the front end:

```powershell
node scripts/verify-setup.mjs
```

It checks the whole stack and prints a pass/fail table grouped by service: `.env`
exists and the secret is long enough, which database is configured, whether the
local container is up, whether the application answers, whether `agent1` can log in, whether the
datasource is reachable, whether an authenticated read succeeds, and — the one
people forget — that an anonymous request is still refused with 401. It exits
non-zero if anything fails, so it also works as a pre-push check.

The "database mode" line reads `.env`, not the running process. If you edit
`.env` while the backend is up, restart it before trusting that line.

Reading the datasource detail needs a login, because `management.endpoint.health.show-details`
is `when-authorized` rather than `always`: the health endpoint stays public so a
container orchestrator can probe it, but an anonymous caller gets only `UP` or
`DOWN`, never the database vendor, connection state, or disk figures.

### If something fails

| Symptom | Cause |
| ------- | ----- |
| `Could not resolve placeholder 'JWT_SECRET'` | `.env` was never copied, or it is not at the repository root |
| `Connection refused` on 5432 | Docker Desktop is not running, or `docker compose up -d` was skipped |
| Login returns 401 immediately after startup | the seeder has not finished; retry in a second |
| Azure connection hangs with no error | blocked by the Azure firewall rules — ask the server owner |
| `password authentication failed` | wrong password, or extra characters pasted into `.env` |

## Download the project

Clone the repository, then enter its directory:

```powershell
git clone <repository-url>
cd java-bootcamp-capstone
```

Replace `<repository-url>` with the GitHub repository URL.

## Run locally

Start Docker Desktop first. From the repository root, start PostgreSQL and Kafka:

```powershell
docker compose up -d
```

Start the backend in a second terminal:

```powershell
cd backend
.\mvnw spring-boot:run
```

Start the front end in a third:

```powershell
cd frontend
npm run dev
```

Open <http://localhost:5173>. The API is at `http://localhost:8080`, PostgreSQL
at `localhost:5432`, and Kafka at `localhost:9092`.

To stop the containers:

```powershell
docker compose down
```

Add `-v` to that only when you want to wipe the database — it deletes the volume,
so the schema and every row go with it.

## Run it on the course cluster

Backend on the shared k3s cluster (one namespace per student), UI on your
laptop proxying to it. Full record, evidence, and demo pre-flight:
`docs/course-cluster-deployment.md`.

### Deploy

1. `copy k8s\cluster.env.example .env.cluster` (works in cmd and PowerShell)
2. Fill `.env.cluster` from your row of the credentials sheet
3. `bash k8s/cluster-deploy.sh` — **in Git Bash**

Safe to re-run: the Secret is created only if absent, everything else is
apply/patch. Step 3 really does need Git Bash — in cmd and PowerShell, plain
`bash` is WSL's, which cannot read Windows paths; from those shells run
`"C:\Program Files\Git\bin\bash.exe" k8s/cluster-deploy.sh` instead (the
script detects WSL and says so rather than failing confusingly).

### View it

1. Create `frontend/.env.local` containing the line `VITE_PROXY_TARGET=http://crm-studentNN.100.22.136.97.nip.io`
2. `npm --prefix frontend run dev`
3. Open <http://localhost:5173> and sign in with the demo accounts below

The browser stays on localhost and Vite forwards `/api` server-side, so CORS
never enters the picture.

### Watch it

Students have no SSH — `kubectl` against the cluster API *is* the terminal on
the backend. Set `KUBECONFIG` to your `studentNN.yaml` once per shell, then:

| What | Command |
| ---- | ------- |
| Pods, live | `kubectl -n studentNN get pods -w` |
| Application log, live | `kubectl -n studentNN logs -f deploy/crm-api` |
| Health through the ingress | `curl http://crm-studentNN.100.22.136.97.nip.io/actuator/health/readiness` |
| Your schema in SQL | `psql -h 100.22.136.97 -U studentNN -d bootcamp` |
| Your Kafka topic, live | `docker run --rm edenhill/kcat:1.7.1 -b 100.22.136.97:9092 -t studentNN.crm.interaction.v1 -C -o beginning` |

Record an interaction in the UI with the log window open: one correlation id,
on the HTTP request line and the Kafka consumer line.

### Break and recover

Rehearsed with dated evidence in `docs/rollback-runbook.md` — a bad image
that never reaches a user, undone with one `kubectl rollout undo`. Rehearse
before demo day, never during.

## API endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/api/v1/auth/login` | Exchange a username and password for a bearer token |
| `GET` | `/api/v1/customers` | List customers |
| `GET` | `/api/v1/customers/{customerId}` | Get one customer |
| `POST` | `/api/v1/customers` | Create a customer |
| `PUT` | `/api/v1/customers/{customerId}` | Update a customer |
| `DELETE` | `/api/v1/customers/{customerId}` | Soft-delete a customer (ADMIN only) |
| `POST` | `/api/v1/interactions` | Publish an interaction event |
| `GET` | `/api/v1/customers/{customerId}/interactions` | Read persisted interaction history |

The interaction endpoint accepts `customerId`, `channel`, and `notes`. It saves
the interaction, publishes the event, and returns `202 Accepted`. The customer
details screen reads the resulting history from the nested GET endpoint.

## Test the project

From the `backend` directory, run unit tests:

```powershell
.\mvnw test
```

Run the complete verification suite:

```powershell
.\mvnw verify
```

`mvn verify` runs the unit tests and the embedded Kafka integration test. The integration test starts its own temporary Kafka broker, so it does not require the Docker Kafka container.

For the final pre-push check:

```powershell
.\mvnw clean verify
```

Run the browser journey after starting PostgreSQL and Kafka with Docker Compose:

```powershell
cd frontend
npm run test:e2e
```

Playwright starts Spring Boot and Vite, exercises login → create customer → log
interaction → read back, and writes its HTML report to
`frontend/playwright-report/`. Use `npm run test:e2e:report` to open it.

## Configuration

### Kafka broker

The backend uses `localhost:9092` by default. Override the broker address with the `KAFKA_BOOTSTRAP_SERVERS` environment variable when needed.

```powershell
$env:KAFKA_BOOTSTRAP_SERVERS = "localhost:9092"
.\mvnw spring-boot:run
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

### Database

Which database is used is chosen by **Spring profile**, not by editing any file.

| Profile | Database | How to select |
| ------- | -------- | ------------- |
| `local` | the docker-compose container | nothing — it is the default |
| `azure` | the hosted Flexible Server | `SPRING_PROFILES_ACTIVE=azure` |
| `test` | in-memory H2 | set automatically when tests run |

`application-local.yml` and `application-azure.yml` hold only the *shape* of each
connection. Every host, name, user and password comes from `.env`, so nothing
about either database is committed — including the local container's password,
which Docker Compose reads from the same file.

`sslmode=require` is built into the azure profile rather than left to whoever
writes `.env`. Azure refuses the connection without it.

Changing `LOCAL_DB_PASSWORD` after the container already exists needs
`docker compose down -v` first. Postgres only applies `POSTGRES_PASSWORD` when it
initialises an empty data directory, so editing `.env` alone leaves the old
password in place and the application then fails to authenticate.

### Deploying

There is no `.env` in a deployed environment. Both imports skip, and the same key names arrive as real environment variables instead — Azure App Service Application Settings, Container Apps secrets, or a Kubernetes Secret. The image is identical on a laptop and in the cloud; only the source of the values changes.

`.env.example` is the committed list of which keys exist. Deploying means copying those keys into the host's settings, never uploading the file.

### Demo accounts

Two accounts are seeded into the `app_user` table on first startup, with BCrypt
password hashes — they are rows in PostgreSQL, not hardcoded users.
`POST /api/v1/auth/login` returns a bearer token to send as
`Authorization: Bearer <token>`.

| Username | Password | Role |
| -------- | -------- | ---- |
| `agent1` | `agent1` | AGENT |
| `admin1` | `admin1` | ADMIN |

`/api/v1/customers` and `/api/v1/interactions` require AGENT or ADMIN. `/api/v1/admin` requires ADMIN. `/api/v1/auth/login` and the Actuator health probes are public.

## Project structure

```text
java-bootcamp-capstone/
├── backend/                 Spring Boot application and tests
├── frontend/                React + TypeScript application (Vite)
├── k8s/                     Manifests, cluster ConfigMap overlay, smoke.sh, cluster-deploy.sh
├── scripts/                 verify-setup.mjs, the one-command setup check
├── docs/                    Planning documents — start with backlog.md
├── defense/                 Reserved for defense material
├── reports/                 Reserved for reports
├── docker-compose.yml       Local PostgreSQL and Kafka
├── .env.example             The list of keys .env must define
└── README.md
```

`docs/` holds the planning material:

| File | What it is for |
| ---- | -------------- |
| `backlog.md` | What we are building, what exists, the phases, and the split of work |
| `architecture.md` | How the system fits together today, with diagrams |
| `lab-coverage.md` | Which course techniques the capstone has actually used |
| `risk-register.md` | Known risks, who owns them, and what was accepted |
| `azure-admin-runbook.md` | Operating the hosted database |
| `azure-authentication.md` | How the Azure connection authenticates |
| `course-cluster-deployment.md` | The course k3s deployment: record, evidence, access model, demo pre-flight |
| `rollback-runbook.md` | Known-good digests and the rehearsed break-and-recover procedure |
