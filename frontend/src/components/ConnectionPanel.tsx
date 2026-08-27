import { useCallback, useEffect, useState } from 'react'
import { healthApi, type HealthResponse, type HealthStatus } from '../api/health'
import { ApiError } from '../api/ApiError'

// What the UI is connected to, made visible in the product.
//
// During the deploy work this was checked by hand every time: curl the health
// endpoint, psql to see whether the database answered, kcat for the broker. The
// first two of those are already in the health payload and already authorized,
// so the app can just show them.
//
// Deliberately not admin-gated. "Is the thing I am looking at actually talking
// to a live backend" is a question any signed-in user has, and the answer is
// not privileged: it is the same detail Actuator hands any authenticated
// caller.

// Rendered detail is an allow-list, not the raw payload.
//
// diskSpace.details carries the absolute path of the working directory, which
// on a laptop is the developer's home directory. That is a path disclosure with
// no diagnostic value here, and dumping components verbatim would put it on
// screen. Only these two keys off the db component are shown.
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

export default function ConnectionPanel() {
  const [probe, setProbe] = useState<Probe>({ state: 'loading' })
  const [checking, setChecking] = useState(false)

  const check = useCallback((signal?: AbortSignal) => {
    setChecking(true)
    return healthApi
      .get(signal)
      .then((health) => setProbe({ state: 'ok', health, at: new Date() }))
      .catch((e) => {
        if (e instanceof ApiError && e.kind === 'abort') return
        // A DOWN backend answers 503 with a body Actuator still shaped, so the
        // status code matters as much as the message: "unreachable" and
        // "reachable but unhealthy" are different problems.
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
  // diagnostic panel, and a timer hitting the backend every few seconds for the
  // life of the tab costs more than it tells anyone.
  useEffect(() => {
    const ctrl = new AbortController()
    check(ctrl.signal)
    return () => ctrl.abort()
  }, [check])

  const health = probe.state === 'ok' ? probe.health : null
  const db = health?.components?.db
  const kafkaReported = Boolean(health?.components && 'kafka' in health.components)

  const backendStatus: HealthStatus | 'UNREACHABLE' =
    probe.state === 'ok' ? probe.health.status : probe.state === 'failed' ? 'UNREACHABLE' : 'UNKNOWN'

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
              <span className={`conn-dot ${statusClass(backendStatus)}`} aria-hidden="true" />
              {probe.state === 'ok' ? (
                <span>{probe.health.status}</span>
              ) : (
                <span>
                  Unreachable
                  {probe.status !== undefined && ` (HTTP ${probe.status})`}
                </span>
              )}
            </dd>

            <dt>Database</dt>
            <dd>
              {db ? (
                <>
                  <span className={`conn-dot ${statusClass(db.status)}`} aria-hidden="true" />
                  <span>{db.status}</span>
                  <span className="muted">
                    {DB_DETAIL_KEYS.map((k) => db.details?.[k])
                      .filter(Boolean)
                      .join(' · ')}
                  </span>
                </>
              ) : (
                // Either the request failed, or it succeeded anonymously and
                // Actuator withheld the components. Both are "not reported".
                <span className="muted">Not reported</span>
              )}
            </dd>

            <dt>Kafka</dt>
            <dd>
              <span className="muted">
                {kafkaReported
                  ? String(health?.components?.kafka?.status)
                  : 'No health indicator is registered for the broker'}
              </span>
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
