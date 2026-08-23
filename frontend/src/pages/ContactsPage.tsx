import DemoTag from '../components/DemoTag'
import { IconUserPlus } from '../components/icons'
import { MOCK_CONTACTS } from '../mock/mockData'

// Screen 4. No backend endpoint for contacts — runs on demo data.
export default function ContactsPage() {
  return (
    <div>
      <div className="page-header">
        <h1>Contacts</h1>
        <button className="btn-primary"><IconUserPlus className="icon" /> &nbsp;Add Contact</button>
      </div>
      <div className="card">
        <div className="toolbar" style={{ marginBottom: '0.6rem' }}>
          <span className="section-title" style={{ margin: 0 }}>All contacts</span>
          <DemoTag />
        </div>
        <div className="table-wrap">
          <table className="data">
            <thead>
              <tr><th>Contact Name</th><th>Designation</th><th>Email</th><th>Phone</th><th>Actions</th></tr>
            </thead>
            <tbody>
              {MOCK_CONTACTS.map((c) => (
                <tr key={c.email}>
                  <td>{c.name}</td>
                  <td>{c.designation}</td>
                  <td>{c.email}</td>
                  <td className="mono">{c.phone}</td>
                  <td className="muted">—</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="muted" style={{ marginTop: '1rem' }}>
          Showing {MOCK_CONTACTS.length} of {MOCK_CONTACTS.length} entries
        </p>
      </div>
    </div>
  )
}
