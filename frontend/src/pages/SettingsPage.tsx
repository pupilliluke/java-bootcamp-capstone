import { useEffect, useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { adminApi } from '../api/admin'
import type { PendingUser } from '../api/admin'
import { ApiError } from '../api/ApiError'
import ConnectionPanel from '../components/ConnectionPanel'

// Issue #16: ADMINs review and approve new self-registered accounts (which are
// created disabled). AGENTs see a notice; the backend also enforces this (403).
export default function SettingsPage() {
  const { state } = useAuth()
  const isAdmin = state.status === 'authenticated' && state.user.role === 'ADMIN'

  const [pending, setPending] = useState<PendingUser[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [approvingId, setApprovingId] = useState<number | null>(null)

  useEffect(() => {
    if (!isAdmin) {
      setLoading(false)
      return
    }
    const ctrl = new AbortController()
    setLoading(true)
    setError('')
    adminApi
      .listPending(ctrl.signal)
      .then(setPending)
      .catch((e) => {
        if (e instanceof ApiError && e.kind === 'abort') return
        setError(e instanceof Error ? e.message : 'Could not load pending accounts')
      })
      .finally(() => setLoading(false))
    return () => ctrl.abort()
  }, [isAdmin])

  async function approve(id: number) {
    setApprovingId(id)
    setError('')
    try {
      await adminApi.enable(id)
      setPending((prev) => prev.filter((u) => u.id !== id))
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Could not approve account')
    } finally {
      setApprovingId(null)
    }
  }

  return (
    <div>
      <div className="page-header">
        <h1>Settings</h1>
      </div>

      <div className="card">
        <p className="section-title">Pending account approvals</p>

        {!isAdmin ? (
          <p className="muted">Only administrators can review and approve new accounts.</p>
        ) : loading ? (
          <div className="spinner-row">Loading pending accounts…</div>
        ) : error ? (
          <p className="error">{error}</p>
        ) : pending.length === 0 ? (
          <p className="empty">No accounts are waiting for approval.</p>
        ) : (
          <div className="table-wrap">
            <table className="data">
              <thead>
                <tr>
                  <th>Username</th>
                  <th>Email</th>
                  <th>Requested</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {pending.map((u) => (
                  <tr key={u.id}>
                    <td>{u.username}</td>
                    <td>{u.email}</td>
                    <td className="muted">
                      {u.createdAt ? new Date(u.createdAt).toLocaleDateString() : '—'}
                    </td>
                    <td>
                      <button
                        className="btn-primary"
                        disabled={approvingId === u.id}
                        onClick={() => approve(u.id)}
                      >
                        {approvingId === u.id ? 'Approving…' : 'Approve'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <ConnectionPanel />
    </div>
  )
}
