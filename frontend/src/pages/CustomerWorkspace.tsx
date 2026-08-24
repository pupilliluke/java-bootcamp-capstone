import { useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import type { Page } from '../nav'
import Sidebar from '../components/Sidebar'
import DashboardPage from './DashboardPage'
import CustomerListPage from './CustomerListPage'
import CustomerDetailsPage from './CustomerDetailsPage'
import CustomerFormPage from './CustomerFormPage'
import ContactsPage from './ContactsPage'
import ActivitiesPage from './ActivitiesPage'
import ReportsPage from './ReportsPage'
import SettingsPage from './SettingsPage'

// The full Customer Management UI, mounted only behind ProtectedRoute. Everything
// here lives inside the guard so signing out unmounts it (and any loaded customer
// data) rather than leaving one agent's session on screen for the next.
//
// Dependency-free navigation: a single `page` state selects the screen (no router
// library). `reloadKey` bumps to refetch lists after a create.
export default function CustomerWorkspace() {
  const { state, logout } = useAuth()
  const [page, setPage] = useState<Page>({ name: 'dashboard' })
  const [reloadKey, setReloadKey] = useState(0)

  const navigate = (p: Page) => setPage(p)
  const refresh = () => setReloadKey((k) => k + 1)

  return (
    <div className="app-shell">
      <Sidebar current={page.name} navigate={navigate} />
      <main className="workspace">
        {state.status === 'authenticated' && (
          <div className="topbar">
            <span className="muted">
              Signed in as <strong>{state.user.username}</strong> ({state.user.role})
            </span>
            <button className="btn-secondary" type="button" onClick={logout}>
              Sign out
            </button>
          </div>
        )}

        {page.name === 'dashboard' && <DashboardPage navigate={navigate} reloadKey={reloadKey} />}
        {page.name === 'customers' && <CustomerListPage navigate={navigate} reloadKey={reloadKey} />}
        {page.name === 'details' && <CustomerDetailsPage customerId={page.customerId} navigate={navigate} />}
        {page.name === 'add' && <CustomerFormPage navigate={navigate} onCreated={refresh} edit={page.edit} />}
        {page.name === 'contacts' && <ContactsPage />}
        {page.name === 'activities' && <ActivitiesPage navigate={navigate} />}
        {page.name === 'reports' && <ReportsPage />}
        {page.name === 'settings' && <SettingsPage />}
      </main>
    </div>
  )
}
