// Verifies a developer's setup and prints a report grouped by area.
//
//   cd backend && mvnw spring-boot:run    # terminal one (./mvnw on macOS)
//   cd frontend && npm run dev            # terminal two
//   node scripts/verify-setup.mjs         # terminal three
//
// Written in Node rather than PowerShell or bash so the same command works on
// Windows 10, Windows 11 and macOS with nothing extra installed — every machine
// on this project already needs Node for the front end.
//
// Two rules for the output:
//   - a healthy area is one line, so a green run is short
//   - a broken area expands into a line per problem, each saying what to do
//
// Exits non-zero if anything failed, so it doubles as a pre-push check.

import { readFileSync, existsSync } from 'node:fs'
import { execFileSync, execSync } from 'node:child_process'
import { createConnection } from 'node:net'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const api = 'http://localhost:8080'
const results = []

const add = (section, name, detail, ok) => results.push({ section, name, ok, detail })

// Prerequisites earn a line only when they fail. Passing, they are implied by
// everything below them working.
const addQuiet = (section, name, detail, ok) => { if (!ok) add(section, name, detail, false) }

// One line for a healthy area; a line per problem when it is not.
const group = (section, name, parts) => {
  const failed = parts.filter((p) => !p.ok)
  if (failed.length === 0) {
    add(section, name, parts.map((p) => p.summary).filter(Boolean).join(', '), true)
    return
  }
  // Only the first failure is a root cause; the rest usually cascade from it.
  add(section, name, failed[0].detail, false)
  for (const f of failed.slice(1)) add(section, `  also: ${f.label}`, f.detail, false)
}

const tcpReachable = (host, port, timeout = 5000) =>
  new Promise((resolve) => {
    const socket = createConnection({ host, port })
    const done = (ok) => { socket.destroy(); resolve(ok) }
    socket.setTimeout(timeout)
    socket.once('connect', () => done(true))
    socket.once('timeout', () => done(false))
    socket.once('error', () => done(false))
  })

const canRun = (cmd, args) => {
  try { execFileSync(cmd, args, { stdio: 'ignore' }); return true } catch { return false }
}

// ============================ configuration ==================================
const envPath = join(root, '.env')
let env = {}
const envExists = existsSync(envPath)

if (envExists) {
  for (const line of readFileSync(envPath, 'utf8').split('\n')) {
    const m = line.match(/^([A-Z_]+)=(.*)$/)
    if (m) env[m[1]] = m[2].trim()
  }
}

const required = ['JWT_SECRET', 'LOCAL_DB_NAME', 'LOCAL_DB_USER', 'LOCAL_DB_PASSWORD']
const gaps = required.filter((k) => !env[k])

group('CONFIGURATION', '.env', [
  {
    label: 'file',
    ok: envExists,
    summary: envExists ? 'loaded' : '',
    detail: 'missing — run: cp .env.example .env   (Windows: copy .env.example .env)',
  },
  {
    label: 'required keys',
    ok: envExists && gaps.length === 0,
    summary: gaps.length === 0 ? `${required.length} required keys present` : '',
    detail: `missing keys: ${gaps.join(', ')} — copy them from .env.example`,
  },
])

// ============================ infrastructure =================================
const dockerUp = canRun('docker', ['info'])
addQuiet('INFRASTRUCTURE', 'docker',
  'daemon not responding — start Docker Desktop, then: docker compose up -d', dockerUp)

let services = {}
if (dockerUp) {
  try {
    const out = execSync('docker compose ps --format "{{.Service}}|{{.Status}}"', {
      cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'],
    })
    for (const line of out.trim().split('\n').filter(Boolean)) {
      const [name, status] = line.split('|')
      services[name] = status
    }
  } catch {
    services = null // compose could not evaluate the file, usually a missing .env
  }
}

const containerDetail = (name) =>
  !dockerUp ? 'unknown — the Docker daemon is not responding'
    : services === null ? 'could not read docker-compose.yml — the LOCAL_DB_* keys in .env are probably missing'
    : `not running — run: docker compose up -d ${name}`

// ============================ database =======================================
const pgPort = Number(env.LOCAL_DB_PORT ?? 5432)
const pgStatus = services === null ? null : services.postgres
const pgListening = pgStatus ? await tcpReachable('localhost', pgPort, 2000) : false

group('DATABASE', 'local', [
  {
    label: 'configuration',
    ok: Boolean(env.LOCAL_DB_NAME && env.LOCAL_DB_USER && env.LOCAL_DB_PASSWORD),
    summary: env.LOCAL_DB_NAME ? `${env.LOCAL_DB_NAME} as ${env.LOCAL_DB_USER}` : '',
    detail: 'LOCAL_DB_* keys missing from .env — docker compose refuses to start without them',
  },
  { label: 'container', ok: Boolean(pgStatus), summary: pgStatus ?? '', detail: containerDetail('postgres') },
  {
    label: 'port',
    ok: pgListening,
    summary: pgListening ? `listening on ${pgPort}` : '',
    detail: `nothing is listening on ${pgPort} — the container may still be starting`,
  },
])

const azureKeys = ['AZURE_DB_HOST', 'AZURE_DB_NAME', 'AZURE_DB_USER', 'AZURE_DB_PASSWORD']
const azureConfigured = azureKeys.some((k) => env[k])
const azureGaps = azureKeys.filter((k) => !env[k])

if (!azureConfigured) {
  add('DATABASE', 'azure', 'not configured — local only, which is fine', true)
} else if (azureGaps.length > 0) {
  add('DATABASE', 'azure', `partly configured, missing: ${azureGaps.join(', ')} — get these from a teammate out of band`, false)
} else {
  const reachable = await tcpReachable(env.AZURE_DB_HOST, Number(env.AZURE_DB_PORT ?? 5432))

  let loginOk = false, loginDetail = '', loginSummary = ''
  if (!reachable) {
    loginDetail = 'not tested — the host did not answer'
  } else if (!canRun('psql', ['--version'])) {
    loginOk = true // a missing client is not a broken setup, but say so
    loginSummary = 'credentials not tested, psql is not installed'
  } else {
    const conn = `host=${env.AZURE_DB_HOST} port=${env.AZURE_DB_PORT ?? 5432} dbname=${env.AZURE_DB_NAME} user=${env.AZURE_DB_USER} sslmode=require connect_timeout=10`
    try {
      // The password goes through the environment, never the command line, so it
      // does not land in shell history or a process listing.
      execFileSync('psql', [conn, '-tAc', 'SELECT 1'], {
        env: { ...process.env, PGPASSWORD: env.AZURE_DB_PASSWORD },
        stdio: ['ignore', 'pipe', 'pipe'], encoding: 'utf8',
      })
      loginOk = true
      loginSummary = `signed in as ${env.AZURE_DB_USER}`
    } catch (err) {
      const msg = String(err.stderr ?? err.message)
      loginDetail =
        /password authentication failed/i.test(msg)
          ? 'wrong password — check AZURE_DB_PASSWORD in .env, or reset it in the Azure portal'
        : /does not exist/i.test(msg)
          ? `the database "${env.AZURE_DB_NAME}" does not exist on that server — create it, or fix AZURE_DB_NAME`
        : /permission denied/i.test(msg)
          ? `signed in, but this user has no rights on ${env.AZURE_DB_NAME} — ask the server owner for a grant`
        : /no pg_hba/i.test(msg)
          ? 'the server refused the connection — public network access may be disabled on it'
        : /SSL|sslmode/i.test(msg)
          ? 'TLS negotiation failed — the azure profile sets sslmode=require, so this is a server-side setting'
        : msg.split('\n').find((l) => l.trim()) ?? 'unknown error'
    }
  }

  group('DATABASE', 'azure', [
    {
      label: 'network',
      ok: reachable,
      summary: reachable ? env.AZURE_DB_HOST : '',
      detail: `${env.AZURE_DB_HOST} did not answer — add your public IP to the Azure firewall rules, or the server is stopped`,
    },
    { label: 'sign in', ok: loginOk, summary: loginSummary, detail: loginDetail },
  ])
}

// ============================ messaging ======================================
const kafkaStatus = services === null ? null : services.kafka
const kafkaListening = kafkaStatus ? await tcpReachable('localhost', 9092, 2000) : false

group('MESSAGING', 'kafka', [
  { label: 'container', ok: Boolean(kafkaStatus), summary: kafkaStatus ?? '', detail: containerDetail('kafka') },
  {
    label: 'port',
    ok: kafkaListening,
    summary: kafkaListening ? 'listening on 9092' : '',
    detail: 'nothing is listening on 9092 — the broker may still be starting',
  },
])

// ============================ backend ========================================
const get = async (path, token) => {
  const headers = token ? { Authorization: `Bearer ${token}` } : {}
  return fetch(`${api}${path}`, { headers, signal: AbortSignal.timeout(5000) })
}

const profile = process.env.SPRING_PROFILES_ACTIVE ?? 'local'
let answered = false, running = false

try {
  const res = await get('/actuator/health')
  const body = await res.json()
  answered = true
  running = body.status === 'UP'
  add('BACKEND', 'application',
    running
      ? `UP on 8080, ${profile} profile`
      : `status ${body.status} — it started but a dependency is failing, usually the database. Check the DATABASE rows above and the backend log`,
    running)
} catch (err) {
  // A refused connection and a timeout look identical to fetch but mean opposite
  // things: nothing listening means it was never started, while a timeout means
  // it is running and blocked, most often on a database that will never answer.
  const timedOut = err?.name === 'TimeoutError' || err?.name === 'AbortError'
  add('BACKEND', 'application',
    timedOut
      ? 'did not answer within 5s — running but blocked, commonly waiting on a database that is down'
      : 'nothing is listening on 8080 — start it with: cd backend && mvnw spring-boot:run',
    false)
}

// ============================ auth ===========================================
const secret = env.JWT_SECRET ?? ''
addQuiet('AUTH', 'jwt secret',
  `${secret.length} characters, needs 32 or more — edit JWT_SECRET in .env. The backend refuses to start below that, and its own error is four exceptions deep`,
  secret.length >= 32)

let token = null
if (answered) {
  try {
    const res = await fetch(`${api}/api/v1/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'agent1', password: 'agent1' }),
      signal: AbortSignal.timeout(10000),
    })
    const body = await res.json()
    token = body.accessToken ?? null

    // Which database answered is folded in here rather than given its own line:
    // signing in already proves it is readable, this just names it.
    let vendor = ''
    if (token) {
      try {
        const detail = await (await get('/actuator/health', token)).json()
        const db = detail.components?.db
        vendor = db?.status === 'UP' ? `, reading ${db.details?.database}` : `, datasource ${db?.status ?? 'unknown'}`
      } catch { vendor = '' }
    }

    add('AUTH', 'sign in',
      body.role === 'AGENT'
        ? `agent1 signed in as AGENT${vendor}`
        : `no token returned (HTTP ${res.status}) — the schema or the seeded accounts are missing. Look for Flyway errors in the backend log`,
      body.role === 'AGENT')
  } catch {
    add('AUTH', 'sign in', 'request failed — the backend cannot reach its database to look the user up', false)
  }

  if (token) {
    const read = await get('/api/v1/customers', token)
    add('AUTH', 'authorised read',
      read.ok ? '/api/v1/customers returned 200'
        : `/api/v1/customers returned ${read.status} — a valid token was refused, or the endpoint errored`,
      read.ok)
  }

  try {
    const res = await get('/api/v1/customers')
    add('AUTH', 'anonymous refused',
      res.status === 401 ? 'HTTP 401'
        : `HTTP ${res.status} — SECURITY IS NOT WORKING. An unauthenticated caller reached customer data. Do not deploy until this is 401`,
      res.status === 401)
  } catch {
    add('AUTH', 'anonymous refused', 'inconclusive — the backend did not answer', false)
  }
}

// ============================ frontend =======================================
const uiUp = await tcpReachable('localhost', 5173, 2000)
add('FRONTEND', 'dev server',
  uiUp ? 'listening on 5173, open http://localhost:5173'
    : 'not running — start it with: cd frontend && npm run dev',
  uiUp)

// ============================ report =========================================
const nameWidth = Math.max(...results.map((r) => r.name.length))
console.log('')
for (const section of [...new Set(results.map((r) => r.section))]) {
  console.log(section)
  for (const r of results.filter((x) => x.section === section)) {
    console.log(`  ${r.ok ? 'PASS' : 'FAIL'}  ${r.name.padEnd(nameWidth)}  ${r.detail}`)
  }
  console.log('')
}

const failed = results.filter((r) => !r.ok).length
console.log(failed === 0
  ? 'Setup verified.'
  : `${failed} check${failed > 1 ? 's' : ''} failed — see the troubleshooting table in README.md`)
if (failed > 0) process.exit(1)
