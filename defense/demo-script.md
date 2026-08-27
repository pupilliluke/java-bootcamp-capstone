# Neural CRM — Live Demo Script

12 minutes. Amina Khan `CUS-1001`, correlation id `lab-request-001`.
Verified on the course cluster 2026-08-27, namespace `student04`.

**Hard rule:** if the defense clock hits minute 36, stop and go to Q&A.
Don't read the SAY lines word for word. They're your track, say them your own way.

## Roles

- **Narrator** (Luke) sets up each beat, watches the clock
- **UI Driver** (Himank) drives the browser
- **Verifier** (Tim) confirms in the API, database, and Kafka
- **Security & Delivery** (Chase) takes the deny and recovery beats

## Pre-flight (before anyone is watching)

- Docker Desktop running
- Terminal 1: `$env:KUBECONFIG = 'C:\Users\himan\.kube\student04.yaml'` then `kubectl -n student04 logs -f deploy/crm-api` (leave running)
- Terminal 2: `kubectl -n student04 get pods -w` (leave running)
- Terminal 3: `npm --prefix frontend run dev` from the repo root (leave running)
- Terminal 4: free, for curl and kcat
- Browser on `http://localhost:5173`, logged out. **Not** the nip.io address, that's the API alone and 401s at the root
- Fallback screenshots open in a background tab
- Accounts: `agent1` / `agent1` (AGENT), `admin1` / `admin1` (ADMIN)
- Nothing sensitive on screen: no tokens, no kubeconfigs, no real emails

---

## 1. Login screen — auth and the security model (~1 min)

**DO**
- Start on the login screen. Don't type yet.

**SAY**
- This is Neural, our CRM for service agents
- Before I touch anything, the security model
- The front end is a React single page app, and it holds no trust of its own
- Every action has to be backed by a signed token from the backend

**DO**
- Log in as `agent1` / `agent1`

**SAY**
- Those credentials go to `POST /api/auth/login`
- Spring Security validates them and returns a signed JWT
- From here the React app attaches that token to every request
- The backend re-checks it on every call
- No sessions, no server-side state, which is what lets us run more than one copy

**DO**
- Verifier points at terminal 1: `login_success user=agent1 role=AGENT`

**PROVES:** real JWT auth, stateless design

---

## 2. Customer list — layered API and the data path (~1 min)

**DO**
- Land on the customer list

**SAY**
- This came from `GET /api/customers`
- It went controller, then service layer holding the business rules, then a Spring Data JPA repository
- The data lives in PostgreSQL on the course server
- Nothing here is mocked, these rows are read live

**DO**
- Type part of a name in the search box
- Tick a status checkbox, for example ACTIVE

**SAY**
- Agents search across name, ID, and email, and filter by status
- Cuts a large book of customers down to who they actually need
- Every call carries a correlation ID header
- That's how we trace one user action across the whole system

**PROVES:** layered architecture, live persistence, correlation IDs

---

## 3. Customer detail — durable persistence (~1 min)

**DO**
- Click `CUS-1001` Amina Khan to open the detail page

**SAY**
- Overview, contacts, and activities
- The status badge is driven by the record's real state in the database, not a UI guess
- This customer survives a backend restart because it's a real row in PostgreSQL
- Earlier in the project this data only lived in memory and vanished on restart
- Making it durable was the core of our persistence work

**PROVES:** durable persistence, customer lifecycle

---

## 4. Record an interaction — the messaging backbone (~2.5 min)

**DO**
- Click the Activities tab
- Point at the "Record interaction (live → backend)" form

**SAY**
- This is the part I most want you to see, it exercises the whole backbone

**DO**
- Channel PHONE, notes mentioning `lab-request-001`
- Click Add Activity

**SAY**
- The request goes to `POST /api/interactions`
- Two things happen, and the order is deliberate
- First it's written to PostgreSQL
- Only after that save succeeds does the backend publish a versioned event to Kafka
- Our topic is `student04.crm.interaction.v1`, namespaced so four teams on one broker don't read each other's events
- That ordering matters: an event never announces something that isn't already safely stored

**DO**
- Point at the Interaction History updating below

**SAY**
- That timeline came back from `GET /api/customers/{id}/interactions`
- Full round trip: UI, API, database, and back
- A consumer picks the event up for processing and audit
- It's idempotent, so the same event delivered twice is still only processed once
- That's what makes it safe under retries

**PROVES:** versioned Kafka events, save-before-publish, idempotent consumer, round trip

---

## 5. Prove it outside the UI (~2 min, the credibility moment)

**SAY**
- I don't want you to take the UI's word for it

**DO**
- Terminal 1, point at the log lines: `POST /api/interactions -> 202`, then `Processed interaction event`

**DO**
- Terminal 4, the event on our own topic:

      docker run --rm edenhill/kcat:1.7.1 -b 100.22.136.97:9092 -t student04.crm.interaction.v1 -C -o beginning

**SAY**
- There's the event, with `"version": 1` and `"correlationId": "lab-request-001"`

**DO**
- Terminal 4, the row in our schema:

      docker run --rm -it -e PGPASSWORD=$env:PGPASSWORD postgres:17 psql -h 100.22.136.97 -U student04 -d bootcamp -c "SELECT interaction_id, customer_id, channel, notes FROM interaction ORDER BY occurred_at DESC LIMIT 5;"

**SAY**
- There's the interaction I just created, same channel, same notes, timestamp from seconds ago
- One correlation id ties the HTTP request, the Kafka event, and the consumer log together
- If something breaks at two in the morning, that's the thread you pull
- The same path is covered by a test that runs against a real PostgreSQL container in CI on every push

**PROVES:** verified UI to database, versioned events, traceability, tested in CI

---

## 6. The deny — RBAC enforced at the API (~1.5 min)

**DO**
- Still as `agent1`, point at the customer header: there's Edit, but no Close

**SAY**
- Access is role-aware
- I'm an agent, and closing a customer is privileged, so I don't have that control

**DO**
- Verifier sends the admin-only call using the agent's token: **403**
- Then an unauthenticated read: **401**

**SAY**
- The missing button is just the UI being polite, it isn't what protects you
- `DELETE /api/customers` requires the ADMIN role at the security layer
- A crafted request with an agent's token is refused regardless of what the browser shows
- The UI and the API agree, and the API is the one that's authoritative

**PROVES:** RBAC enforced server-side, not cosmetically

---

## 7. Recovery — kill it and watch it heal (~2.5 min)

Chase narrates.

**DO**
- Terminal 4: `kubectl -n student04 delete pod -l app=crm-api`
- Watch terminal 2 go `Terminating`, `ContainerCreating`, `Running`

**SAY**
- I just killed the running pod
- I'm not going to type a recovery command, because I don't have to
- Kubernetes sees it's a pod short and starts another, about a second after I hit enter
- Readiness holds traffic back until the app is genuinely serving

**SAY while it comes back (81 seconds, don't stand there silent)**
- Three probes do the work: startup gates liveness, readiness gates traffic, liveness restarts a hang
- I'll be straight about the trade-off: single replica, so this costs about eighty seconds of downtime
- It heals itself, but it isn't zero-downtime, and more replicas is the fix
- We wrote that down as a residual risk with an owner rather than leaving it out

**DO**
- When readiness returns, refresh the page. The interaction is still there.

**SAY**
- Nothing was ever stored in the pod, it's all in PostgreSQL, so the new one reads the same rows back
- Worth separating two things: this handles a pod dying, it does not handle a bad image
- A bad image means the rollout stalls and the old pod keeps serving, which is contained but not fixed
- Fixing that is a deliberate rollback to the last good digest, and CI proves it on every push

**PROVES:** automatic self-healing, state durability, honest residual risk, rollback distinct from self-healing

---

## 8. Close (~0.5 min)

**SAY**
- So that's the stack working together
- React front end behind real tokens, layered Spring Boot API
- Data that actually persists, schema owned by Flyway
- Versioned Kafka events with a consumer that handles duplicates
- Deployed to Kubernetes by digest, so what's running is exactly what we tested
- And it recovers on its own when you kill it
- Every claim has a link to the proof in the evidence index
- Happy to go deeper on any layer, or show the pipeline and rollback path

---

## Delivery tips

- **If a step errors live:** stay calm, glance at terminal 1, narrate it. "Let me check the logs, this is exactly the correlation-ID tracing I mentioned." A recovered error demos observability better than a clean run
- **Don't debug in silence.** Say what broke, switch to the screenshot, keep moving
- **If the cluster is down:** `docker compose up -d`, rename `frontend\.env.local`, run backend and frontend locally. Same script, just say "local PostgreSQL" instead of the course server
- **If Kafka or psql won't cooperate:** `kubectl -n student04 logs deploy/crm-api` shows the consumer handling the event
- **Avoid these screens.** Sidebar Activities, Contacts, and Reports are still on mock data. The customer's own Activities tab is the real one
- **Don't read the SAY lines verbatim.** They're your track, not a teleprompter

## Where the proof lives

- Recovery, both kinds: [../docs/recovery.md](../docs/recovery.md)
- Rollback and last known-good digest: [../docs/rollback-runbook.md](../docs/rollback-runbook.md)
- Cluster deployment: [../docs/course-cluster-deployment.md](../docs/course-cluster-deployment.md)
- Probes and digest pin: [../k8s/deployment.yaml](../k8s/deployment.yaml)
- Threats and residual risk: [../docs/threat-model.md](../docs/threat-model.md), [../docs/risk-register.md](../docs/risk-register.md)
- Everything mapped to the rubric: [evidence-index.md](evidence-index.md)
