// ⚠️ MOCK DATA — NOT FROM THE BACKEND.
// The backend has no endpoints for contacts, activity history, or report
// aggregates, so these screens run on hardcoded demo data. Everything that
// consumes this file also shows a visible "Demo data" tag in the UI so it is
// never mistaken for real, persisted data. Delete this file (and its screens)
// once real endpoints exist.

import type { Activity, Contact } from '../types/customer'

export const MOCK_CONTACTS: Contact[] = [
  { name: 'John Smith', designation: 'Sales Manager', email: 'john.smith@acme.com', phone: '(555) 111-2222' },
  { name: 'Mary Johnson', designation: 'Account Manager', email: 'mary.j@acme.com', phone: '(555) 222-3333' },
  { name: 'Robert Brown', designation: 'Support Lead', email: 'robert.b@acme.com', phone: '(555) 333-4444' },
]

export const MOCK_ACTIVITIES: Activity[] = [
  { date: 'May 01, 2024', type: 'Call', subject: 'Quarterly Follow-up', assignedTo: 'Jennifer Lee', status: 'Completed' },
  { date: 'Apr 28, 2024', type: 'Email', subject: 'Product Information', assignedTo: 'Michael Chen', status: 'Completed' },
  { date: 'Apr 20, 2024', type: 'Meeting', subject: 'Requirement Discussion', assignedTo: 'Jennifer Lee', status: 'Completed' },
  { date: 'Apr 10, 2024', type: 'Task', subject: 'Pricing Proposal', assignedTo: 'Michael Chen', status: 'Pending' },
  { date: 'Apr 05, 2024', type: 'Call', subject: 'Introductory Call', assignedTo: 'Jennifer Lee', status: 'Completed' },
]

// Report aggregates (screen 6). Derived shapes for the hand-rolled SVG charts.
export const MOCK_REPORTS = {
  totalCustomers: 125,
  activeCustomers: 98,
  inactiveCustomers: 27,
  byStatus: [
    { label: 'Active', value: 98, color: '#16a34a' },
    { label: 'Inactive', value: 27, color: '#dc2626' },
  ],
  byIndustry: [
    { label: 'Manufacturing', value: 40, color: '#2563eb' },
    { label: 'Technology', value: 30, color: '#3b82f6' },
    { label: 'Retail', value: 25, color: '#60a5fa' },
    { label: 'Other', value: 30, color: '#93c5fd' },
  ],
  topCustomers: [
    { name: 'Acme Corporation', revenue: 125000 },
    { name: 'Globex Inc.', revenue: 98500 },
    { name: 'Stark Industries', revenue: 75000 },
    { name: 'Umbrella Corp.', revenue: 60750 },
    { name: 'Initech', revenue: 45250 },
  ],
}
