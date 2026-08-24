import DemoTag from '../components/DemoTag'
import { IconActivities } from '../components/icons'
import type { Navigate } from '../nav'
import { MOCK_ACTIVITIES } from '../mock/mockData'

// Screen 5 (global view). There is no GET to list interactions, so this history
// is demo data. Real recording lives on a customer's Activities tab.
export default function ActivitiesPage({ navigate }: { navigate: Navigate }) {
  return (
    <div>
      <div className="page-header">
        <h1>Activities</h1>
        <button className="btn-secondary" onClick={() => navigate({ name: 'customers' })}>
          <IconActivities className="icon" /> &nbsp;Record on a customer
        </button>
      </div>
      <div className="card">
        <div className="toolbar" style={{ marginBottom: '0.6rem' }}>
          <span className="section-title" style={{ margin: 0 }}>Activity history</span>
          <DemoTag />
        </div>
        <p className="muted" style={{ marginTop: 0 }}>
          To record a real interaction, open a customer → Activities tab (posts to{' '}
          <code>/api/interactions</code>).
        </p>
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr><th>Date</th><th>Type</th><th>Subject</th><th>Assigned To</th><th>Status</th></tr>
            </thead>
            <tbody>
              {MOCK_ACTIVITIES.map((a, i) => (
                <tr key={i}>
                  <td>{a.date}</td>
                  <td>{a.type}</td>
                  <td>{a.subject}</td>
                  <td>{a.assignedTo}</td>
                  <td><span className={`badge badge-${a.status}`}>{a.status}</span></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}
