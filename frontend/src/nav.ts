import type { Customer } from './types/customer'

// Dependency-free navigation model: the app switches screens with plain React
// state instead of a router library (keeps us inside bootcamp tooling).
export type Page =
  | { name: 'dashboard' }
  | { name: 'customers' }
  | { name: 'details'; customerId: string }
  | { name: 'add'; edit?: Customer }
  | { name: 'contacts' }
  | { name: 'activities' }
  | { name: 'reports' }
    | { name: 'admin-users' }
  | { name: 'settings' }

export type Navigate = (page: Page) => void
