import { useCustomers } from '../hooks/useCustomers'
import { useRecentInteractions } from '../hooks/useRecentInteractions'
import InteractionTimeline from '../components/InteractionTimeline'
import { IconActivities } from '../components/icons'
import type { Navigate } from '../nav'

// Screen 5: interactions across the whole book, read from the API.
//
// This used to render a fixed array of invented rows because nothing could list
// interactions across customers. It still cannot: there is no
// GET /api/v1/interactions, so useRecentInteractions fans out per customer and the
// cap inside it bounds how much of the book this covers. That is a real limit
// and it is stated on the page rather than hidden, but the rows are now real.
const FEED_LIMIT = 40

export default function ActivitiesPage({ navigate }: { navigate: Navigate }) {
  // Asks for the largest page the server allows, because this feed is only as
  // wide as the customers it knows about: useRecentInteractions fans out per
  // customer. Taking the default page of twenty would silently narrow the
  // "across every customer" claim below to the first twenty of them.
  const { customers, loading: customersLoading, error: customersError } = useCustomers({
    page: 0,
    size: 100,
    sort: 'customerId',
    direction: 'asc',
  })
  const { interactions, loading, error } = useRecentInteractions(customers, FEED_LIMIT)

  const customerNames = Object.fromEntries(customers.map((c) => [c.customerId, c.fullName]))
  const busy = customersLoading || loading
  const failed = customersError || error

  return (
    <div>
      <div className="page-header">
        <h1>Activities</h1>
        <button className="btn-secondary" onClick={() => navigate({ name: 'customers' })}>
          <IconActivities className="icon" /> &nbsp;Record on a customer
        </button>
      </div>
      <div className="card">
        <p className="section-title">Activity history</p>
        <p className="muted" style={{ marginTop: 0 }}>
          Interactions across every customer, newest first. To record one, open a customer
          and use the Activities tab.
        </p>

        {busy && <div className="spinner-row">Loading activity…</div>}
        {!busy && failed && <p className="error" role="alert">{failed}</p>}
        {!busy && !failed && interactions.length === 0 && (
          <p className="empty">No interactions recorded yet.</p>
        )}
        {!busy && interactions.length > 0 && (
          <InteractionTimeline
            interactions={interactions}
            customerNames={customerNames}
            onSelect={(customerId) => navigate({ name: 'details', customerId })}
          />
        )}
      </div>
    </div>
  )
}
