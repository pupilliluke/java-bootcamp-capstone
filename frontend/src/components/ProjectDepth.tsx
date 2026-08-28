// The project, in depth, as collapsible sections on Settings — the defense's
// six course areas, each a one-line summary and the short true points behind
// it. Everything here is verifiable in the repository; nothing is aspiration.
//
// The diagram is styled text on purpose: this frontend keeps its dependency
// list to React alone, so no mermaid renderer. The full diagram set (container
// view, request paths, auth flow, messaging, deployment) lives in
// docs/architecture.md and docs/architecture/context.md.

type Section = {
  title: string
  summary: string
  points: string[]
  lessons: string[]
}

const SECTIONS: Section[] = [
  {
    title: '1 · Java & the JVM',
    summary: 'Java 21 records, streams, custom exceptions; fail-loud configuration at startup.',
    points: [
      'Every request and response body is a record — immutable, no boilerplate.',
      'Service-layer failures are named exceptions (CustomerNotFound, DuplicateCustomer, LastAdmin), each mapped to one HTTP status.',
      'JWT_SECRET has no default: a missing secret crashes startup instead of shipping a weak one.',
      'Hibernate validates the schema against Flyway and refuses to start on drift — the database is never silently altered.',
    ],
    lessons: [
      'Failing at startup beats limping in production.',
      'One exception handler means one error shape a client can rely on.',
    ],
  },
  {
    title: '2 · Backend & testing',
    summary: 'Maven wrapper + enforcer, layered packages, DTO validation, JUnit/Mockito — and an honest AI-assistance note.',
    points: [
      'The wrapper pins Maven and the enforcer pins JDK 21, so every machine builds the same way.',
      'Packages are layers: api, service, repository, entity, messaging, security, observability.',
      'Validation lives on the DTO — the channel enum returns 400 with a field error, and a CHECK constraint backs it at the table.',
      'The suite runs on H2 in PostgreSQL mode with the real migrations, plus integration tests on real PostgreSQL, embedded Kafka, and the built container.',
      'A Playwright journey drives login → create → interact → read back in CI on every pull request.',
      'defense/ai-assistance.md records where AI helped and where its first answer was rejected.',
    ],
    lessons: [
      'The schema under test must be the schema that ships.',
      'A test that mocks the seam proves nothing about the seam — the 503 health bug hid exactly there.',
      'Going green is easy; making it go red on purpose is the verification.',
    ],
  },
  {
    title: '3 · Spring',
    summary: 'Constructor injection, profiles, @Transactional, deny-by-default security with real JWT.',
    points: [
      'Constructor injection everywhere — no field magic, everything testable.',
      'Profiles select the world: local (compose), azure (hosted, TLS required), test (in-memory).',
      'Recording an interaction is @Transactional: the row commits before the event publishes.',
      'Security ends in denyAll — a new endpoint is closed until someone opens it (ADR-001 covers the real JWT).',
      'Login failures are uniform 401s: an attacker cannot probe which usernames exist.',
      'Matcher order is deliberate: DELETE /customers is admin-only before the general agent rule.',
    ],
    lessons: [
      'Deny-by-default turns forgetting into safety instead of exposure.',
      'The rule that depends on the request body lives in the service, where the body is visible.',
    ],
  },
  {
    title: '4 · Kafka, React & PostgreSQL',
    summary: 'A versioned, keyed, idempotent event pipeline; a typed React client; a Flyway-owned database.',
    points: [
      'Events publish to crm.interaction.v1, keyed by customer so one customer stays ordered.',
      'The producer is idempotent with acks=all; the consumer checks the version, dedupes against a durable table, and parks poison messages on a DLT.',
      'The group-splitting incident is documented: a laptop consumer in the shared group silently stole partition 0 from the deployed pod.',
      'React calls a typed api layer; the token lives in memory, never localStorage; any 401 returns cleanly to login.',
      'PostgreSQL is Flyway-owned, V1 through V8: users, interactions, customers, sequences, dedupe, indexes, constraints, audit columns.',
    ],
    lessons: [
      'Idempotency that lives in memory dies with the pod — a replay after restart is exactly what a failure looks like.',
      'A dev instance pointed at shared infrastructure is a member of production.',
    ],
  },
  {
    title: '5 · DevOps',
    summary: 'A four-times-checked non-root image, digest-pinned everywhere, with rehearsed failure and recovery.',
    points: [
      'Multi-stage Dockerfile, non-root uid, digest-pinned base images, revision label and env.',
      'Four image gates in CI: hadolint, container-structure-test, Trivy (gating, with an owned triage file), and a Testcontainers boot test.',
      'Every develop/main build publishes to GHCR; the Deployment pins the digest, not a tag.',
      'Readiness includes the database; liveness deliberately does not — a dead database must not crash-loop every pod.',
      'smoke.sh deploys, breaks the deployment on purpose, proves the ingress kept serving, and recovers — on every pull request.',
      'The 81-second pod-kill self-heal and the rollback procedure are recorded in docs/recovery.md and the runbook.',
    ],
    lessons: [
      'A tag moves; a digest is the build you actually verified.',
      'A gate that only runs after merge is a report, not a gate.',
      'A call that can hang needs a timeout, or no retry loop can save it.',
    ],
  },
  {
    title: '6 · Capstone',
    summary: 'C4 diagrams, measurable NFRs, eleven ADRs, a live risk register — and this panel as evidence.',
    points: [
      'Architecture is drawn twice: the C4 context with trust boundaries, and the container view with request paths.',
      'NFRs are numbers with units and a way to measure each one.',
      'Eleven ADRs record what was decided, what was rejected, and why — including the API/event versioning split.',
      'The risk register scores risks and records accepted ones with an owner and a date.',
      'This connection panel is itself evidence: the deployed pod names its commit, environment, and connections live.',
    ],
    lessons: [
      'An honest gap named in advance beats a polished claim someone else punctures.',
    ],
  },
]

export default function ProjectDepth() {
  return (
    <div className="card" style={{ marginTop: '1.1rem' }}>
      <p className="section-title">The project, in depth</p>

      <details className="conn-details">
        <summary>How it fits together</summary>
        <pre className="depth-diagram" aria-label="System map">
{`browser ── https ──▶ Vercel edge (neuralcrm.xyz)
                        │  /api, /actuator proxied server-side
                        ▼
                 Spring Boot API ── JDBC ──▶ PostgreSQL (Flyway V1–V8)
                        │
                 crm.interaction.v1 (keyed by customer)
                        ▼
                 consumer ── dedupe table ──▶ processed · retries ▶ DLT`}
        </pre>
        <p className="muted">
          Full diagrams — container view, request paths, auth flow, messaging,
          deployment — are in <span className="mono">docs/architecture.md</span>{' '}
          and the C4 context in <span className="mono">docs/architecture/context.md</span>.
        </p>
      </details>

      {SECTIONS.map((s) => (
        <details className="conn-details" key={s.title}>
          <summary>{s.title} — {s.summary}</summary>
          <ul className="depth-points">
            {s.points.map((p) => <li key={p}>{p}</li>)}
            {s.lessons.map((l) => (
              <li key={l} className="depth-lesson">Lesson: {l}</li>
            ))}
          </ul>
        </details>
      ))}
    </div>
  )
}
