import { useState } from 'react'
import DemoTag from '../components/DemoTag'
import DonutChart from '../components/DonutChart'
import BarChart from '../components/BarChart'
import { IconUsers } from '../components/icons'
import { MOCK_REPORTS } from '../mock/mockData'

const MENU = [
  'Customer Summary',
  'Sales by Customer',
  'Outstanding Invoices',
  'Customer Aging',
  'Top Customers',
  'New Customers',
  'Activity Summary',
]

// Screen 6. No aggregation endpoint exists — the whole report runs on demo data.
export default function ReportsPage() {
  const [active, setActive] = useState('Customer Summary')
  const r = MOCK_REPORTS
  const statusTotal = r.byStatus.reduce((s, d) => s + d.value, 0)

  return (
    <div>
      <div className="page-header">
        <h1>Customer Reports</h1>
        <DemoTag />
      </div>

      <div className="reports-layout">
        <div className="card report-menu">
          <p className="section-title">Reports</p>
          {MENU.map((m) => (
            <button key={m} className={active === m ? 'active' : ''} onClick={() => setActive(m)}>
              {m}
            </button>
          ))}
        </div>

        <div>
          <div className="kpi-row">
            <Tile label="Total Customers" value={r.totalCustomers} tone="blue" />
            <Tile label="Active Customers" value={r.activeCustomers} tone="green" />
            <Tile label="Inactive Customers" value={r.inactiveCustomers} tone="red" />
          </div>

          <div className="chart-grid" style={{ marginTop: '1.1rem' }}>
            <div className="card">
              <p className="section-title">Customers by Status</p>
              <div style={{ display: 'flex', gap: '1.2rem', alignItems: 'center' }}>
                <DonutChart data={r.byStatus} />
                <div className="chart-legend">
                  {r.byStatus.map((d) => (
                    <div key={d.label} className="legend-item">
                      <span className="legend-swatch" style={{ background: d.color }} />
                      {d.label} — {Math.round((d.value / statusTotal) * 100)}% ({d.value})
                    </div>
                  ))}
                </div>
              </div>
            </div>

            <div className="card">
              <p className="section-title">Customers by Industry</p>
              <BarChart data={r.byIndustry} />
              <div className="chart-legend" style={{ marginTop: '0.6rem' }}>
                {r.byIndustry.map((d) => (
                  <div key={d.label} className="legend-item">
                    <span className="legend-swatch" style={{ background: d.color }} />
                    {d.label} ({d.value})
                  </div>
                ))}
              </div>
            </div>
          </div>

          <div className="card" style={{ marginTop: '1.1rem' }}>
            <p className="section-title">Top Customers by Revenue</p>
            <div className="table-wrap">
              <table className="data">
                <thead><tr><th>Customer Name</th><th>Revenue (USD)</th></tr></thead>
                <tbody>
                  {r.topCustomers.map((c) => (
                    <tr key={c.name}>
                      <td>{c.name}</td>
                      <td className="mono">${c.revenue.toLocaleString()}.00</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
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
