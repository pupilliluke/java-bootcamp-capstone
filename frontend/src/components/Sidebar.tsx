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

type NavItem = {
  key: Page['name']
  label: string
  page: Page
  Icon: (props: { className?: string }) => JSX.Element
  adminOnly?: boolean
}

const ITEMS: NavItem[] = [
  {
    key: 'dashboard',
    label: 'Dashboard',
    page: { name: 'dashboard' },
    Icon: IconDashboard,
  },
  {
    key: 'customers',
    label: 'Customers',
    page: { name: 'customers' },
    Icon: IconUsers,
  },
  {
    key: 'add',
    label: 'Add Customer',
    page: { name: 'add' },
    Icon: IconUserPlus,
  },
  {
    key: 'contacts',
    label: 'Contacts',
    page: { name: 'contacts' },
    Icon: IconContacts,
  },
  {
    key: 'activities',
    label: 'Activities',
    page: { name: 'activities' },
    Icon: IconActivities,
  },
  {
    key: 'reports',
    label: 'Reports',
    page: { name: 'reports' },
    Icon: IconReports,
  },
  {
    key: 'admin-users',
    label: 'User Management',
    page: { name: 'admin-users' },
    Icon: IconUsers,
    adminOnly: true,
  },
  {
    key: 'settings',
    label: 'Settings',
    page: { name: 'settings' },
    Icon: IconSettings,
  },
]

interface SidebarProps {
  current: Page['name']
  navigate: Navigate
  isAdmin: boolean
}

export default function Sidebar({current, navigate, isAdmin, }: SidebarProps) {
  // The customer details screen belongs to the "Customers" section.
  const active = current === 'details' ? 'customers' : current
  const visibleItems = ITEMS.filter(
      (item) => !item.adminOnly || isAdmin,
  )

  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <span className="logo">★</span>
        <span>Northstar CRM</span>
      </div>
      <nav className="nav">
        {visibleItems.map(({ key, label, page, Icon }) => (
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
