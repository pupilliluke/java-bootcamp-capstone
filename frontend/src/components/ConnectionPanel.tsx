import { useCallback, useEffect, useState, version as reactVersion } from 'react'
import { healthApi, type HealthResponse, type HealthStatus } from '../api/health'
import { infoApi, type InfoResponse } from '../api/info'
import { ApiError } from '../api/ApiError'

// What the UI is connected to, made visible in the product.
//
// During the deploy work this was checked by hand every time: curl the health
// endpoint, psql to see whether the database answered, kcat for the broker.
// Health answers "is it up"; /actuator/info answers "up against what" -- the
// profile, the database target, the topic, the running version. Both are
// already authorized to any signed-in user, so the app can just show them.
//
// Deliberately not admin-gated. "Is the thing I am looking at actually talking
// to a live backend" is a question any signed-in user has, and the answer is
// not privileged: it is the same detail Actuator hands any authenticated
// caller.

// Rendered health detail is an allow-list, not the raw payload.
//
// diskSpace.details carries the absolute path of the working directory, which
// on a laptop is the developer's home directory. That is a path disclosure
// with no diagnostic value here, and dumping components verbatim would put it
// on screen. Only these two keys off the db component are shown. The info
// payload needs no allow-list because the server curates it before it leaves
// (ConnectionInfoContributor) -- the sanitizing happens where the data is.
const DB_DETAIL_KEYS = ['database', 'validationQuery'] as const

type Probe =
  | { state: 'loading' }
  | { state: 'ok'; health: HealthResponse; at: Date }
  | { state: 'failed'; message: string; status?: number; at: Date }

function statusClass(status: HealthStatus | 'UNREACHABLE'): string {
  if (status === 'UP') return 'conn-up'
  if (status === 'DOWN' || status === 'UNREACHABLE') return 'conn-down'
  return 'conn-unknown'
}

function StatusDot({ status }: { status: HealthStatus | 'UNREACHABLE' }) {
  return <span className={`conn-dot ${statusClass(status)}`} aria-hidden="true" />
}

export default function ConnectionPanel() {
  const [probe, setProbe] = useState<Probe>({ state: 'loading' })
  // Info arrives separately and is allowed to fail separately: a backend that
  // answers health but predates the info contributor still gets a working
  // panel, just with "not reported" in the identity rows.
  const [info, setInfo] = useState<InfoResponse | null>(null)
  const [checking, setChecking] = useState(false)

  const check = useCallback((signal?: AbortSignal) => {
    setChecking(true)
    infoApi
      .get(signal)
      .then(setInfo)
      .catch(() => setInfo(null))
    return healthApi
      .get(signal)
      .then((health) => setProbe({ state: 'ok', health, at: new Date() }))
      .catch((e) => {
        if (e instanceof ApiError && e.kind === 'abort') return
        // With 503 handled as data in the transport, reaching this catch means
        // the backend genuinely did not answer usefully: a network failure, or
        // a status no health endpoint sends. "Unreachable" is now accurate
        // rather than a catch-all.
        setProbe({
          state: 'failed',
          message: e instanceof Error ? e.message : 'Could not reach the backend',
          status: e instanceof ApiError ? e.status : undefined,
          at: new Date(),
        })
      })
      .finally(() => setChecking(false))
  }, [])

  // Checked once on mount and then only when asked. No polling: this is a
  // diagnostic panel, and a timer hitting the backend every few seconds for
  // the life of the tab costs more than it tells anyone.
  useEffect(() => {
    const ctrl = new AbortController()
    check(ctrl.signal)
    return () => ctrl.abort()
  }, [check])

  const health = probe.state === 'ok' ? probe.health : null
  const db = health?.components?.db
  const kafkaHealth = health?.components?.kafka
  const connections = info?.connections
  const build = info?.build

  const backendStatus: HealthStatus | 'UNREACHABLE' =
    probe.state === 'ok' ? probe.health.status : probe.state === 'failed' ? 'UNREACHABLE' : 'UNKNOWN'

  const backendIdentity = [
    build?.version && `v${build.version}`,
    info?.revision && info.revision.slice(0, 12),
    // Derived server-side: "kubernetes: student02" from the platform's own
    // signals, or "profile: local" on a laptop. Never a declared label.
    connections?.environment,
  ]
    .filter(Boolean)
    .join(' · ')

  const dbIdentity = [
    connections?.database && `db ${connections.database}`,
    connections?.schema && `schema ${connections.schema}`,
    ...DB_DETAIL_KEYS.map((k) => db?.details?.[k]).filter(Boolean),
  ]
    .filter(Boolean)
    .join(' · ')

  return (
    <div className="card" style={{ marginTop: '1.1rem' }}>
      <div className="toolbar" style={{ marginBottom: '0.6rem' }}>
        <span className="section-title" style={{ margin: 0 }}>Connection</span>
        <button
          className="btn-secondary"
          onClick={() => check()}
          disabled={checking}
          aria-label="Refresh connection status"
        >
          {checking ? 'Checking…' : 'Refresh'}
        </button>
      </div>

      {probe.state === 'loading' ? (
        <div className="spinner-row">Checking connection…</div>
      ) : (
        <>
          <dl className="conn-list">
            <dt>Backend</dt>
            <dd>
              <StatusDot status={backendStatus} />
              {probe.state === 'ok' ? (
                <span>{probe.health.status}</span>
              ) : (
                <span>
                  Unreachable
                  {probe.status !== undefined && ` (HTTP ${probe.status})`}
                </span>
              )}
              {backendIdentity && <span className="muted">{backendIdentity}</span>}
            </dd>

            <dt>Database</dt>
            <dd>
              {db ? (
                <>
                  <StatusDot status={db.status} />
                  <span>{db.status}</span>
                </>
              ) : (
                // Either the request failed, or it succeeded anonymously and
                // Actuator withheld the components. Both are "not reported".
                <span className="muted">Not reported</span>
              )}
              {dbIdentity && <span className="muted mono">{dbIdentity}</span>}
            </dd>

            <dt>Kafka</dt>
            <dd>
              {kafkaHealth ? (
                <>
                  <StatusDot status={kafkaHealth.status} />
                  <span>{kafkaHealth.status}</span>
                </>
              ) : (
                <span className="muted">No health indicator is registered for the broker</span>
              )}
              {connections?.kafka && (
                <span className="muted mono">
                  {[connections.kafka.topic, connections.kafka.consumerGroup]
                    .filter(Boolean)
                    .join(' · ')}
                </span>
              )}
            </dd>

            <dt>Serving this UI</dt>
            <dd>
              <span className="mono">{window.location.origin}</span>
            </dd>

            <dt>API</dt>
            <dd>
              <span className="muted">Same origin, proxied — no cross-origin calls</span>
            </dd>
          </dl>

          {info?.runtime && (
            <details className="conn-details">
              <summary>Runtime, in depth</summary>
              <dl className="conn-list">
                <dt>Java</dt>
                <dd><span className="mono">{[info.runtime.java?.version, info.runtime.java?.vendor].filter(Boolean).join(' · ')}</span></dd>
                <dt>Dependencies</dt>
                <dd>
                  <span className="mono">
                    {Object.entries(info.runtime.dependencies ?? {})
                      .map(([k, v]) => `${k} ${v}`)
                      .join(' · ')}
                  </span>
                </dd>
                <dt>OS</dt>
                <dd><span className="mono">{[info.runtime.os?.name, info.runtime.os?.arch].filter(Boolean).join(' · ')}</span></dd>
                <dt>This UI</dt>
                <dd><span className="mono">React {reactVersion} · {import.meta.env.MODE} build</span></dd>
              </dl>
            </details>
          )}

          <details className="conn-details">
            <summary>Where this can run</summary>
            <dl className="conn-list">
              <dt>Environments</dt>
              <dd><span className="muted">laptop (profile: local) · k3d (kubernetes: crm) · course cluster (kubernetes: studentNN) · CI (profile: test)</span></dd>
              <dt>Profiles</dt>
              <dd><span className="muted">local — compose Postgres + Kafka · azure — hosted Flexible Server, TLS required · test — in-memory H2 + embedded Kafka</span></dd>
              <dt>Databases</dt>
              <dd><span className="muted">crm (compose or Azure) · bootcamp, one schema per student (course) · crm in-memory (tests)</span></dd>
              <dt>Brokers</dt>
              <dd><span className="muted">local, unprefixed topic · course, studentNN-prefixed topic and group · embedded (tests)</span></dd>
            </dl>
          </details>

          {probe.state === 'failed' && (
            <p className="error" role="alert">{probe.message}</p>
          )}

          <p className="muted" style={{ marginTop: '0.6rem' }}>
            Checked at {probe.at.toLocaleTimeString()}
          </p>
        </>
      )}
    </div>
  )
}
