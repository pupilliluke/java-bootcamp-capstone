import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import ReportsPage from './ReportsPage'

// ReportsPage runs on static demo aggregates + hand-rolled SVG charts —
// populated state only.
describe('ReportsPage', () => {
  it('renders the report KPIs, charts and top-customers table', () => {
    render(<ReportsPage />)
    expect(screen.getByText('Customer Reports')).toBeInTheDocument()
    expect(screen.getByText(/demo data/i)).toBeInTheDocument()
    expect(screen.getByText('Total Customers')).toBeInTheDocument()
    expect(screen.getByText('Acme Corporation')).toBeInTheDocument()
  })
})
