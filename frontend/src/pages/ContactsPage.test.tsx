import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import ContactsPage from './ContactsPage'

// ContactsPage runs on static demo data, so it only has a populated state.
describe('ContactsPage', () => {
  it('renders the demo contacts with the "Demo data" tag', () => {
    render(<ContactsPage />)
    expect(screen.getByText('Contacts')).toBeInTheDocument()
    expect(screen.getByText(/demo data/i)).toBeInTheDocument()
    expect(screen.getByText('John Smith')).toBeInTheDocument()
    expect(screen.getByText('Sales Manager')).toBeInTheDocument()
  })
})
