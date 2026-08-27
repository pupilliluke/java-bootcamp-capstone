import { useCustomers } from '../hooks/useCustomers'
import { useCustomerCount } from '../hooks/useCustomerCount'
import StatusBadge from '../components/StatusBadge'
import PendingApprovalsNotice from '../components/PendingApprovalsNotice'
import AdminOnly from '../auth/AdminOnly'
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
  // Newest five, ordered by the server. This used to take the tail of the whole
  // customer array, which only worked because the whole array was here.
  const { customers: recent, totalElements, loading, error } = useCustomers({
    reloadKey,
    page: 0,
    size: 5,
    sort: 'createdAt',
    direction: 'desc',
  })

  // Counts come from totals, not from counting rows. Filtering a five-row page
  // by status would report "Active: 3" for a book of a thousand.
  const active = useCustomerCount(['ACTIVE'], reloadKey)
  const prospects = useCustomerCount(['PROSPECT'], reloadKey)

  return (
    <div>
      <div className="page-header">
        <h1>Dashboard</h1>
        <button className="btn-primary" onClick={() => navigate({ name: 'add' })}>Add Customer</button>
      </div>

      {/* Admin-only, and gated at the mount so an AGENT never makes the request.
          Independent of the customer fetch below -- it is about pending accounts,
          not customers, so it shows whatever the customer list is doing. */}
      <AdminOnly fallback={null}>
        <PendingApprovalsNotice navigate={navigate} reloadKey={reloadKey} />
      </AdminOnly>

      {loading && <div className="spinner-row">Loading…</div>}
      {error && <p className="error">{error} — is the backend running on :8080?</p>}

      {!loading && !error && (
        <>
          <div className="kpi-row">
            <Tile label="Total Customers" value={totalElements} tone="blue" />
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
