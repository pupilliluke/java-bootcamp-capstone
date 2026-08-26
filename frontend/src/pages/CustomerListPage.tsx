import { useMemo, useState } from 'react'
import { useCustomers } from '../hooks/useCustomers'
import type { Navigate } from '../nav'
import type { CustomerStatus } from '../types/customer'
import StatusBadge from '../components/StatusBadge'
import Pagination from '../components/Pagination'
import { IconEdit, IconEye, IconSearch, IconUserPlus } from '../components/icons'

const PAGE_SIZE = 8
const STATUSES: CustomerStatus[] = ['ACTIVE', 'PROSPECT', 'SUSPENDED', 'CLOSED']

// Title-case a status for the checkbox label: ACTIVE -> Active.
const label = (s: string) => s.charAt(0) + s.slice(1).toLowerCase()

export default function CustomerListPage({
  navigate,
  reloadKey,
}: {
  navigate: Navigate
  reloadKey: number
}) {
  const { customers, loading, error } = useCustomers(reloadKey)
  const [query, setQuery] = useState('')
  // An empty set means "no status filter" — i.e. All. Selecting any status
  // narrows to just the checked ones; clearing them all falls back to All.
  const [selected, setSelected] = useState<Set<CustomerStatus>>(new Set())
  const [page, setPage] = useState(1)

  function showAll() {
    setSelected(new Set())
    setPage(1)
  }

  function toggle(status: CustomerStatus) {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(status)) next.delete(status)
      else next.add(status)
      return next
    })
    setPage(1)
  }

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    return customers.filter((c) => {
      const matchesQ =
        !q ||
        c.fullName.toLowerCase().includes(q) ||
        c.customerId.toLowerCase().includes(q) ||
        c.email.toLowerCase().includes(q)
      const matchesS = selected.size === 0 || selected.has(c.status)
      return matchesQ && matchesS
    })
  }, [customers, query, selected])

  // Clamped before slicing, not after. The previous version derived a safePage
  // for the pager while the table kept slicing with the raw `page`, so a list
  // that shrank underneath a page > 1 — someone closes customers and the
  // refetch returns fewer rows — showed "No customers match your search" while
  // the pager highlighted page 1 and reported "Showing 1 to 8 of N entries".
  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const safePage = Math.min(page, pageCount)
  const pageRows = filtered.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE)

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

          <div className="status-filter" role="group" aria-label="Filter by status">
            <span className="label">Status:</span>
            <label className="status-check">
              <input type="checkbox" checked={selected.size === 0} onChange={showAll} />
              All
            </label>
            {STATUSES.map((s) => (
              <label key={s} className="status-check">
                <input
                  type="checkbox"
                  checked={selected.has(s)}
                  onChange={() => toggle(s)}
                />
                {label(s)}
              </label>
            ))}
          </div>
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
