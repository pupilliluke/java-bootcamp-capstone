# Demo talking points

Cue cards for [demo-script.md](demo-script.md). Glance at these, don't read them.
Each line is a point to make, not a sentence to recite.

---

## 1. Sign in (1 min)

**Do:** `agent1` / `agent1` at `localhost:5173`

- The front end doesn't get to decide anything on its own
- Credentials go to the API, a signed token comes back
- Backend re-checks that token on every single call
- No session on the server, so we can run more than one copy
- Point at the log line: `login_success user=agent1 role=AGENT`

**If they ask:** a disabled account can't sign in at all, the check happens before
anything else

---

## 2. Find Amina (1 min)

**Do:** search `CUS-1001`, open the profile

- This is coming out of PostgreSQL on the course server, right now
- Not mocked, not seeded in the browser
- Search works across name, ID, and email
- Every call carries a correlation id (sets up the next beat)

---

## 3. Record the interaction (3 min)

**Do:** Activities tab, PHONE, notes, submit

- The order is the point: database first, Kafka second
- An event never tells you about something that isn't already stored
- The event is versioned, so the contract can change without breaking readers
- The consumer is idempotent, send it the same thing twice and it only counts once
- Anything it can't handle goes to a dead letter topic instead of vanishing

**Tim shows three things:**
1. Log: `POST /api/interactions -> 202`, then `Processed interaction event`
2. kcat: the event sitting on `student04.crm.interaction.v1`
3. psql: the actual row in our schema

**The line to land:** one correlation id, `lab-request-001`, ties the HTTP request,
the Kafka event, and the consumer log together

---

## 4. The deny (2 min)

**Do:** show there's no Close button, then show the refused call

- Closing a customer is admin-only, so as an agent I don't see the button
- That's the UI being polite. It isn't what's protecting you
- Agent's token against the admin route: **403**
- No token at all: **401**
- The decision happens at the API, not in the browser

**If they ask:** the role comes from the token, and the UI reads the same role the
backend enforces, so they can't drift apart

---

## 5. Recovery (4 min)

**Do:** `kubectl -n student04 delete pod -l app=crm-api`, watch the `-w` terminal

- I'm not typing a recovery command, because I don't need to
- Kubernetes sees it's a pod short and starts another one, roughly a second later
- Readiness holds traffic back until the app is genuinely serving
- When we rehearsed it: **81 seconds** to fully recover

**Fill the wait, don't stand there silent:**
- Three probes: startup gates liveness, readiness gates traffic, liveness restarts a hang
- Single replica, so this costs about eighty seconds of downtime
- It heals itself, but it isn't zero-downtime, and more replicas is the fix
- We wrote that down as a residual risk with an owner instead of leaving it out

**When it's back:** refresh, the interaction is still there

- Nothing was ever stored in the pod, it's all in PostgreSQL

**Separate the two mechanisms:**
- This handles a pod dying. It does not handle a bad image
- A bad image means the rollout stalls and the old pod keeps serving
- That's contained, but not fixed
- Fixing it is a deliberate rollback to the last good digest, and CI proves it on
  every push

---

## 6. Wrap (1 min)

- React front end behind real tokens
- Layered Spring Boot API
- Data that actually persists, schema owned by Flyway
- Versioned Kafka events, consumer handles duplicates
- Deployed by digest, so what's running is what we tested
- Recovers on its own when you kill it
- Every claim has a link in the evidence index

---

## Lines worth having ready

- "What's running is exactly what we tested" for the digest pin
- "An event never tells you about something that isn't already durable" for the ordering
- "The UI is being polite, the API is what decides" for the deny
- "Nothing was ever stored in the pod" for recovery
- "We wrote it down as a residual risk with an owner" for any gap they find

## Don't say

- "It should work." Either show it, or go to the fallback
- "That's just a demo problem." Name what broke and move on
- Anything that sounds like a scan was softened or a test skipped to get green
