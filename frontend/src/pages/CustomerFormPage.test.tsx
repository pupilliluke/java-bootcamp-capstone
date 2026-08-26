import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import CustomerFormPage from './CustomerFormPage'
import { ApiError } from '../api/ApiError'

// Hoisted so the mock factory below can close over them: vi.mock is lifted
// above the imports, and a plain const would not exist yet when it runs.
const { create, update } = vi.hoisted(() => ({ create: vi.fn(), update: vi.fn() }))
vi.mock('../api/customers', () => ({ customersApi: { create, update } }))

const navigate = vi.fn()
const onCreated = vi.fn()

beforeEach(() => {
  vi.clearAllMocks()
})

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

  it('prefills the form and allows saving in edit mode', () => {
    render(<CustomerFormPage navigate={navigate} onCreated={onCreated} edit={editCustomer} />)
    expect(screen.getByText('Edit Customer')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Acme Corporation')).toHaveValue('Amina Khan')
    expect(screen.getByRole('button', { name: /^save$/i })).toBeEnabled()
  })

  // The behaviour worth pinning. Before PUT existed the form called create() in
  // both modes, so editing an existing customer hit the duplicate-id check and
  // failed. A regression to that would still render fine and still look right.
  it('saves an edit through update, not create', async () => {
    update.mockResolvedValue(editCustomer)
    const user = userEvent.setup()
    render(<CustomerFormPage navigate={navigate} onCreated={onCreated} edit={editCustomer} />)

    await user.clear(screen.getByPlaceholderText('(555) 123-4567'))
    await user.type(screen.getByPlaceholderText('(555) 123-4567'), '555-0199')
    await user.click(screen.getByRole('button', { name: /^save$/i }))

    await waitFor(() =>
      expect(update).toHaveBeenCalledWith(
        'CUS-1001',
        expect.objectContaining({ phone: '555-0199', fullName: 'Amina Khan' }),
      ),
    )
    expect(create).not.toHaveBeenCalled()
  })

  // Whatever reason the server gives for refusing a save, the form has to say
  // so — a save that quietly does nothing reads as a broken button.
  it('shows the reason when the server refuses the save', async () => {
    update.mockRejectedValue(
      new ApiError('Email already in use by another customer', 'http', 409),
    )
    const user = userEvent.setup()
    render(<CustomerFormPage navigate={navigate} onCreated={onCreated} edit={editCustomer} />)

    await user.click(screen.getByRole('button', { name: /^save$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /email already in use by another customer/i,
    )
    // Still on the form, with the input preserved, rather than navigated away.
    expect(navigate).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: /^save$/i })).toBeEnabled()
  })
})
