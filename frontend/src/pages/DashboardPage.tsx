import { useCustomers } from '../hooks/useCustomers'
import { useRecentInteractions } from '../hooks/useRecentInteractions'
import StatusBadge from '../components/StatusBadge'
import InteractionTimeline from '../components/InteractionTimeline'
import { IconUsers } from '../components/icons'
import type { Navigate } from '../nav'

// Real dashboard: every number here is derived from GET /api/customers.
export default function DashboardPage({
  navigate,
  reloadKey,
}: {
  navigate: Navigate
  reloadKey: number
}) {
  const { customers, loading, error } = useCustomers(reloadKey)
  const active = customers.filter((c) => c.status === 'ACTIVE').length
  const prospects = customers.filter((c) => c.status === 'PROSPECT').length
  const recent = [...customers].slice(-5).reverse()

  const { interactions, loading: activityLoading, error: activityError } =
    useRecentInteractions(customers)

  // Id -> name, so the feed can say who each interaction belongs to without
  // the timeline component needing to know what a customer is.
  const customerNames = Object.fromEntries(customers.map((c) => [c.customerId, c.fullName]))

  const today = new Date().toDateString()
  const loggedToday = interactions.filter(
    (it) => new Date(it.createdAt).toDateString() === today,
  ).length

  return (
    <div>
      <div className="page-header">
        <h1>Dashboard</h1>
        <button className="btn-primary" onClick={() => navigate({ name: 'add' })}>Add Customer</button>
      </div>

      {loading && <div className="spinner-row">Loading…</div>}
      {error && <p className="error">{error} — is the backend running on :8080?</p>}

      {!loading && !error && (
        <>
          <div className="kpi-row">
            <Tile label="Total Customers" value={customers.length} tone="blue" />
            <Tile label="Active" value={active} tone="green" />
            <Tile label="Prospects" value={prospects} tone="blue" />
            <Tile label="Logged today" value={loggedToday} tone="amber" />
          </div>

          <div className="card" style={{ marginTop: '1.1rem' }}>
            <p className="section-title">Recent customers</p>
            <div className="table-wrap">
              <table className="data">
                <thead><tr><th>Customer ID</th><th>Name</th><th>Email</th><th>Status</th></tr></thead>
                <tbody>
                  {recent.map((c) => (
                    <tr key={c.customerId} className="clickable" onClick={() => navigate({ name: 'details', customerId: c.customerId })}>
                      <td className="mono">{c.customerId}</td>
                      <td>{c.fullName}</td>
                      <td>{c.email}</td>
                      <td><StatusBadge status={c.status} /></td>
                    </tr>
                  ))}
                  {recent.length === 0 && <tr><td colSpan={4} className="empty">No customers yet.</td></tr>}
                </tbody>
              </table>
            </div>
          </div>

          <div className="card" style={{ marginTop: '1.1rem' }}>
            <p className="section-title">Recent activity</p>
            {activityLoading && <div className="spinner-row">Loading activity…</div>}
            {activityError && <p className="error" role="alert">{activityError}</p>}
            {!activityLoading && !activityError && interactions.length === 0 && (
              <p className="empty">
                No interactions recorded yet. Open a customer and use the Activities tab.
              </p>
            )}
            {interactions.length > 0 && (
              <InteractionTimeline
                interactions={interactions}
                customerNames={customerNames}
                onSelect={(customerId) => navigate({ name: 'details', customerId })}
              />
            )}
          </div>
        </>
      )}
    </div>
  )
}

function Tile({ label, value, tone }: { label: string; value: number; tone: 'blue' | 'green' | 'red' | 'amber' }) {
  return (
    <div className="kpi-tile">
      <div>
        <div className="label">{label}</div>
        <div className="value">{value}</div>
      </div>
      <div className={`kpi-icon ${tone}`}><IconUsers /></div>
    </div>
  )
}
