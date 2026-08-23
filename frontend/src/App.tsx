import { useState } from 'react'
import type { Page } from './nav'
import Sidebar from './components/Sidebar'
import DashboardPage from './pages/DashboardPage'
import CustomerListPage from './pages/CustomerListPage'
import CustomerDetailsPage from './pages/CustomerDetailsPage'
import CustomerFormPage from './pages/CustomerFormPage'
import ContactsPage from './pages/ContactsPage'
import ActivitiesPage from './pages/ActivitiesPage'
import ReportsPage from './pages/ReportsPage'
import SettingsPage from './pages/SettingsPage'

// Composition root. Dependency-free navigation: a single `page` state selects the
// screen (no router library). `reloadKey` bumps to refetch lists after a create.
export default function App() {
  const [page, setPage] = useState<Page>({ name: 'dashboard' })
  const [reloadKey, setReloadKey] = useState(0)

  const navigate = (p: Page) => setPage(p)
  const refresh = () => setReloadKey((k) => k + 1)

  return (
    <div className="app-shell">
      <Sidebar current={page.name} navigate={navigate} />
      <main className="workspace">
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
