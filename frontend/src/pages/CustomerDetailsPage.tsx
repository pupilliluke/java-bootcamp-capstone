import { useState } from 'react'
import { useCustomer } from '../hooks/useCustomer'
import { interactionsApi } from '../api/interactions'
import { ApiError } from '../api/ApiError'
import type { Channel, Interaction } from '../types/customer'
import type { Navigate } from '../nav'
import StatusBadge from '../components/StatusBadge'
import DemoTag from '../components/DemoTag'
import { IconBuilding } from '../components/icons'
import { MOCK_ACTIVITIES, MOCK_CONTACTS } from '../mock/mockData'

type Tab = 'overview' | 'contacts' | 'activities'
const CHANNELS: Channel[] = ['PHONE', 'EMAIL', 'CHAT']

export default function CustomerDetailsPage({
  customerId,
  navigate,
}: {
  customerId: string
  navigate: Navigate
}) {
  const { customer, loading, error } = useCustomer(customerId)
  const [tab, setTab] = useState<Tab>('overview')

  // Real interaction recorder (POST /api/interactions). Timeline is local:
  // the backend is fire-and-forget over Kafka with no GET to list.
  const [channel, setChannel] = useState<Channel>('PHONE')
  const [notes, setNotes] = useState('')
  const [saving, setSaving] = useState(false)
  const [recorded, setRecorded] = useState<Interaction[]>([])
  const [note, setNote] = useState('')

  async function recordInteraction(e: React.FormEvent) {
    e.preventDefault()
    if (!notes.trim()) return
    setSaving(true)
    setNote('')
    try {
      const saved = await interactionsApi.create({ customerId, channel, notes: notes.trim() })
      setRecorded((prev) => [saved, ...prev])
      setNotes('')
      setNote('✓ Sent to backend (POST /api/interactions)')
    } catch (err) {
      const detail = err instanceof ApiError ? ` (${err.kind}${err.status ? ' ' + err.status : ''})` : ''
      setNote(`Shown locally only — could not reach the interaction endpoint${detail}`)
      setRecorded((prev) => [
        { channel, notes: notes.trim(), createdAt: new Date().toISOString() },
        ...prev,
      ])
      setNotes('')
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <div className="spinner-row">Loading customer…</div>
  if (error || !customer)
    return (
      <div>
        <button className="crumb" onClick={() => navigate({ name: 'customers' })}>← Back to List</button>
        <p className="error">{error || 'Customer not found'} — is the backend running on :8080?</p>
      </div>
    )

  return (
    <div>
      <button className="crumb" onClick={() => navigate({ name: 'customers' })}>← Back to List</button>
      <div className="page-header">
        <h1>Customer Details</h1>
        <button className="btn-primary" onClick={() => navigate({ name: 'add', edit: customer })}>Edit</button>
      </div>

      <div className="card">
        <div className="profile-head">
          <div className="avatar"><IconBuilding /></div>
          <div>
            <h2>{customer.fullName} <StatusBadge status={customer.status} /></h2>
            <div className="profile-contact">
              {customer.customerId} · {customer.email} · {customer.phone || 'no phone'}
            </div>
          </div>
        </div>

        <div className="tabs" style={{ marginTop: '1.2rem' }}>
          <button className={`tab${tab === 'overview' ? ' active' : ''}`} onClick={() => setTab('overview')}>Overview</button>
          <button className={`tab${tab === 'contacts' ? ' active' : ''}`} onClick={() => setTab('contacts')}>Contacts</button>
          <button className={`tab${tab === 'activities' ? ' active' : ''}`} onClick={() => setTab('activities')}>Activities</button>
        </div>

        {tab === 'overview' && (
          <dl className="kv">
            <dt>Customer ID</dt><dd className="mono">{customer.customerId}</dd>
            <dt>Full Name</dt><dd>{customer.fullName}</dd>
            <dt>Email</dt><dd>{customer.email}</dd>
            <dt>Phone</dt><dd>{customer.phone || '—'}</dd>
            <dt>Status</dt><dd><StatusBadge status={customer.status} /></dd>
            <dt>Customer Since</dt>
            <dd>{customer.createdAt ? new Date(customer.createdAt).toLocaleDateString() : '—'}</dd>
          </dl>
        )}

        {tab === 'contacts' && (
          <div>
            <div className="toolbar" style={{ marginBottom: '0.6rem' }}>
              <span className="section-title" style={{ margin: 0 }}>Contacts</span>
              <DemoTag />
            </div>
            <div className="table-wrap">
              <table className="data">
                <thead><tr><th>Contact Name</th><th>Designation</th><th>Email</th><th>Phone</th></tr></thead>
                <tbody>
                  {MOCK_CONTACTS.map((c) => (
                    <tr key={c.email}>
                      <td>{c.name}</td><td>{c.designation}</td><td>{c.email}</td><td className="mono">{c.phone}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {tab === 'activities' && (
          <div>
            <p className="section-title">Record interaction (live → backend)</p>
            <form onSubmit={recordInteraction}>
              <div className="form-grid">
                <div className="form-field">
                  <label htmlFor="ch">Channel</label>
                  <select id="ch" value={channel} onChange={(e) => setChannel(e.target.value as Channel)}>
                    {CHANNELS.map((c) => <option key={c}>{c}</option>)}
                  </select>
                </div>
                <div className="form-field full">
                  <label htmlFor="nt">Notes</label>
                  <textarea id="nt" value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Called about renewal" />
                </div>
              </div>
              <div style={{ marginTop: '0.7rem' }}>
                <button className="btn-primary" type="submit" disabled={saving || !notes.trim()}>
                  {saving ? 'Sending…' : 'Add Activity'}
                </button>
              </div>
              {note && <p className="note">{note}</p>}
            </form>

            {recorded.length > 0 && (
              <>
                <p className="section-title" style={{ marginTop: '1.2rem' }}>This session</p>
                <div className="table-wrap">
                  <table className="data">
                    <thead><tr><th>Channel</th><th>Notes</th><th>Recorded</th></tr></thead>
                    <tbody>
                      {recorded.map((it, i) => (
                        <tr key={i}>
                          <td><span className="badge badge-channel">{it.channel}</span></td>
                          <td>{it.notes}</td>
                          <td className="muted">{new Date(it.createdAt).toLocaleString()}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </>
            )}

            <div className="toolbar" style={{ margin: '1.4rem 0 0.6rem' }}>
              <span className="section-title" style={{ margin: 0 }}>Past activity history</span>
              <DemoTag />
            </div>
            <div className="table-wrap">
              <table className="data">
                <thead><tr><th>Date</th><th>Type</th><th>Subject</th><th>Assigned To</th><th>Status</th></tr></thead>
                <tbody>
                  {MOCK_ACTIVITIES.map((a, i) => (
                    <tr key={i}>
                      <td>{a.date}</td><td>{a.type}</td><td>{a.subject}</td><td>{a.assignedTo}</td>
                      <td><span className={`badge badge-${a.status}`}>{a.status}</span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
