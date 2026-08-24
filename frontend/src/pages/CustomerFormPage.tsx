import { useState } from 'react'
import { customersApi } from '../api/customers'
import { ApiError } from '../api/ApiError'
import type { Customer, CustomerStatus } from '../types/customer'
import type { Navigate } from '../nav'

const STATUSES: CustomerStatus[] = ['ACTIVE', 'PROSPECT', 'SUSPENDED', 'CLOSED']

export default function CustomerFormPage({
  navigate,
  onCreated,
  edit,
}: {
  navigate: Navigate
  onCreated: () => void
  edit?: Customer
}) {
  const isEdit = !!edit
  const [customerId, setCustomerId] = useState(edit?.customerId ?? '')
  const [fullName, setFullName] = useState(edit?.fullName ?? '')
  const [email, setEmail] = useState(edit?.email ?? '')
  const [phone, setPhone] = useState(edit?.phone ?? '')
  const [status, setStatus] = useState<CustomerStatus>(edit?.status ?? 'ACTIVE')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const valid = customerId.trim() && fullName.trim() && email.trim()

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!valid || isEdit) return
    setSaving(true)
    setError(null)
    try {
      await customersApi.create({
        customerId: customerId.trim(),
        fullName: fullName.trim(),
        email: email.trim(),
        phone: phone.trim() || undefined,
        status,
      })
      onCreated()
      navigate({ name: 'details', customerId: customerId.trim() })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save customer')
    } finally {
      setSaving(false)
    }
  }

  return (
    <div>
      <button className="crumb" onClick={() => navigate({ name: 'customers' })}>← Back to List</button>
      <div className="page-header">
        <h1>{isEdit ? 'Edit Customer' : 'Add Customer'}</h1>
        <div className="actions-row">
          <button className="btn-secondary" onClick={() => navigate({ name: 'customers' })}>Cancel</button>
          <button className="btn-primary" form="cust-form" type="submit" disabled={!valid || saving || isEdit}>
            {saving ? 'Saving…' : 'Save'}
          </button>
        </div>
      </div>

      <div className="card">
        <p className="section-title">Customer Information</p>

        {isEdit && (
          <p className="note" style={{ marginTop: 0 }}>
            ⚠️ Editing is read-only for now — the backend has no <code>PUT /api/customers</code>{' '}
            endpoint, so changes can’t be saved yet.
          </p>
        )}

        <form id="cust-form" onSubmit={handleSubmit}>
          <div className="form-grid">
            <div className="form-field">
              <label>Customer ID <span className="req">*</span></label>
              <input value={customerId} disabled={isEdit} onChange={(e) => setCustomerId(e.target.value)} placeholder="CUS-1006" />
            </div>
            <div className="form-field">
              <label>Status</label>
              <select value={status} disabled={isEdit} onChange={(e) => setStatus(e.target.value as CustomerStatus)}>
                {STATUSES.map((s) => <option key={s}>{s}</option>)}
              </select>
            </div>
            <div className="form-field">
              <label>Full Name <span className="req">*</span></label>
              <input value={fullName} disabled={isEdit} onChange={(e) => setFullName(e.target.value)} placeholder="Acme Corporation" />
            </div>
            <div className="form-field">
              <label>Email <span className="req">*</span></label>
              <input value={email} disabled={isEdit} onChange={(e) => setEmail(e.target.value)} placeholder="info@acme.com" />
            </div>
            <div className="form-field">
              <label>Phone</label>
              <input value={phone} disabled={isEdit} onChange={(e) => setPhone(e.target.value)} placeholder="(555) 123-4567" />
            </div>
          </div>

          {error && <p className="error" role="alert">{error}</p>}
        </form>
      </div>
    </div>
  )
}
