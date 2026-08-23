import type { Navigate, Page } from '../nav'
import {
  IconActivities,
  IconContacts,
  IconDashboard,
  IconReports,
  IconSettings,
  IconUserPlus,
  IconUsers,
} from './icons'

const ITEMS: { key: Page['name']; label: string; page: Page; Icon: (p: { className?: string }) => JSX.Element }[] = [
  { key: 'dashboard', label: 'Dashboard', page: { name: 'dashboard' }, Icon: IconDashboard },
  { key: 'customers', label: 'Customers', page: { name: 'customers' }, Icon: IconUsers },
  { key: 'add', label: 'Add Customer', page: { name: 'add' }, Icon: IconUserPlus },
  { key: 'contacts', label: 'Contacts', page: { name: 'contacts' }, Icon: IconContacts },
  { key: 'activities', label: 'Activities', page: { name: 'activities' }, Icon: IconActivities },
  { key: 'reports', label: 'Reports', page: { name: 'reports' }, Icon: IconReports },
  { key: 'settings', label: 'Settings', page: { name: 'settings' }, Icon: IconSettings },
]

export default function Sidebar({ current, navigate }: { current: Page['name']; navigate: Navigate }) {
  // The customer details screen belongs to the "Customers" section.
  const active = current === 'details' ? 'customers' : current

  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <span className="logo">★</span>
        <span>Northstar CRM</span>
      </div>
      <nav className="nav">
        {ITEMS.map(({ key, label, page, Icon }) => (
          <button
            key={key}
            className={`nav-item${active === key ? ' active' : ''}`}
            onClick={() => navigate(page)}
          >
            <Icon className="icon" />
            <span className="txt">{label}</span>
          </button>
        ))}
      </nav>
    </aside>
  )
}
