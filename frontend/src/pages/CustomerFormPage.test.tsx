import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi } from 'vitest'
import CustomerFormPage from './CustomerFormPage'

const navigate = vi.fn()
const onCreated = vi.fn()

const editCustomer = {
  customerId: 'CUS-1001',
  fullName: 'Amina Khan',
  email: 'amina.khan@example.test',
  phone: '555-0101',
  status: 'ACTIVE' as const,
}

describe('CustomerFormPage', () => {
  it('renders the add form with Save disabled until required fields are filled', () => {
    render(<CustomerFormPage navigate={navigate} onCreated={onCreated} />)
    expect(screen.getByText('Add Customer')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^save$/i })).toBeDisabled()
  })

  it('enables Save once id, name and email are entered', async () => {
    const user = userEvent.setup()
    render(<CustomerFormPage navigate={navigate} onCreated={onCreated} />)
    await user.type(screen.getByPlaceholderText('CUS-1006'), 'CUS-1009')
    await user.type(screen.getByPlaceholderText('Acme Corporation'), 'Test Co')
    await user.type(screen.getByPlaceholderText('info@acme.com'), 'test@example.com')
    expect(screen.getByRole('button', { name: /^save$/i })).toBeEnabled()
  })

  it('shows the read-only notice in edit mode', () => {
    render(<CustomerFormPage navigate={navigate} onCreated={onCreated} edit={editCustomer} />)
    expect(screen.getByText('Edit Customer')).toBeInTheDocument()
    // The "no PUT endpoint" note renders a <code> element we can match cleanly.
    expect(screen.getByText('PUT /api/customers')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^save$/i })).toBeDisabled()
  })
})
