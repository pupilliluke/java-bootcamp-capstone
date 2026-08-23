import { useCustomers } from '../hooks/useCustomers'
import StatusBadge from '../components/StatusBadge'
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
        </>
      )}
    </div>
  )
}

function Tile({ label, value, tone }: { label: string; value: number; tone: 'blue' | 'green' | 'red' }) {
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
