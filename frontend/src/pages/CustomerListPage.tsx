import { useMemo, useState } from 'react'
import { useCustomers } from '../hooks/useCustomers'
import type { Navigate } from '../nav'
import type { CustomerStatus } from '../types/customer'
import StatusBadge from '../components/StatusBadge'
import Pagination from '../components/Pagination'
import { IconEdit, IconEye, IconSearch, IconUserPlus } from '../components/icons'

const PAGE_SIZE = 8
const STATUSES: (CustomerStatus | 'ALL')[] = ['ALL', 'ACTIVE', 'PROSPECT', 'SUSPENDED', 'CLOSED']

export default function CustomerListPage({
  navigate,
  reloadKey,
}: {
  navigate: Navigate
  reloadKey: number
}) {
  const { customers, loading, error } = useCustomers(reloadKey)
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<CustomerStatus | 'ALL'>('ALL')
  const [page, setPage] = useState(1)

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    return customers.filter((c) => {
      const matchesQ =
        !q ||
        c.fullName.toLowerCase().includes(q) ||
        c.customerId.toLowerCase().includes(q) ||
        c.email.toLowerCase().includes(q)
      const matchesS = status === 'ALL' || c.status === status
      return matchesQ && matchesS
    })
  }, [customers, query, status])

  const pageRows = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)

  // keep page in range when filters shrink the list
  const safePage = pageRows.length === 0 && page > 1 ? 1 : page

  return (
    <div>
      <div className="page-header">
        <h1>Customers</h1>
        <button className="btn-primary" onClick={() => navigate({ name: 'add' })}>
          <IconUserPlus className="icon" /> &nbsp;Add Customer
        </button>
      </div>

      <div className="card">
        <div className="toolbar">
          <div className="search">
            <IconSearch className="icon" />
            <input
              placeholder="Search customers…"
              value={query}
              onChange={(e) => {
                setQuery(e.target.value)
                setPage(1)
              }}
            />
          </div>
          <select
            value={status}
            onChange={(e) => {
              setStatus(e.target.value as CustomerStatus | 'ALL')
              setPage(1)
            }}
            style={{ width: 'auto' }}
          >
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {s === 'ALL' ? 'All statuses' : s}
              </option>
            ))}
          </select>
        </div>

        {loading && <div className="spinner-row">Loading customers…</div>}
        {error && <p className="error" role="alert">{error} — is the backend running on :8080?</p>}

        {!loading && !error && (
          <>
            <div className="table-wrap">
              <table className="data">
                <thead>
                  <tr>
                    <th>Customer ID</th>
                    <th>Customer Name</th>
                    <th>Email</th>
                    <th>Phone</th>
                    <th>Status</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {pageRows.map((c) => (
                    <tr key={c.customerId} className="clickable" onClick={() => navigate({ name: 'details', customerId: c.customerId })}>
                      <td className="mono">{c.customerId}</td>
                      <td>{c.fullName}</td>
                      <td>{c.email}</td>
                      <td className="mono">{c.phone || '—'}</td>
                      <td><StatusBadge status={c.status} /></td>
                      <td onClick={(e) => e.stopPropagation()}>
                        <div className="actions-row">
                          <button className="btn-icon" title="View" onClick={() => navigate({ name: 'details', customerId: c.customerId })}>
                            <IconEye />
                          </button>
                          <button className="btn-icon" title="Edit" onClick={() => navigate({ name: 'add', edit: c })}>
                            <IconEdit />
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                  {pageRows.length === 0 && (
                    <tr>
                      <td colSpan={6} className="empty">No customers match your search.</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
            <Pagination page={safePage} pageSize={PAGE_SIZE} total={filtered.length} onPage={setPage} />
          </>
        )}
      </div>
    </div>
  )
}
