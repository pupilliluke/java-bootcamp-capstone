import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi } from 'vitest'
import ActivitiesPage from './ActivitiesPage'

// ActivitiesPage runs on static demo data — populated state only.
describe('ActivitiesPage', () => {
  it('renders the demo activity history with the "Demo data" tag', () => {
    render(<ActivitiesPage navigate={vi.fn()} />)
    expect(screen.getByText('Activities')).toBeInTheDocument()
    expect(screen.getByText(/demo data/i)).toBeInTheDocument()
    expect(screen.getByText('Quarterly Follow-up')).toBeInTheDocument()
    expect(screen.getByText('Introductory Call')).toBeInTheDocument()
  })
})
