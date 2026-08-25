import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import CustomerDetailsPage from './CustomerDetailsPage'
import { useCustomer } from '../hooks/useCustomer'

vi.mock('../hooks/useCustomer')
const mockUseCustomer = vi.mocked(useCustomer)

// Hoisted so the mock factories below can close over them: vi.mock is lifted
// above the imports, so a plain const would not exist yet when it runs.
const { remove } = vi.hoisted(() => ({ remove: vi.fn() }))
vi.mock('../api/customers', () => ({ customersApi: { remove } }))

const auth = vi.hoisted(() => ({
  state: { status: 'authenticated', user: { username: 'admin1', role: 'ADMIN' } } as {
    status: string
    user: { username: string; role: string }
  },
}))
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ state: auth.state, login: vi.fn(), logout: vi.fn() }),
}))

const navigate = vi.fn()
const renderPage = () =>
  render(<CustomerDetailsPage customerId="CUS-1001" navigate={navigate} />)

const customer = {
  customerId: 'CUS-1001',
  fullName: 'Amina Khan',
  email: 'amina.khan@example.test',
  phone: '555-0101',
  status: 'ACTIVE' as const,
}

const asAdmin = () => {
  auth.state = { status: 'authenticated', user: { username: 'admin1', role: 'ADMIN' } }
}
const asAgent = () => {
  auth.state = { status: 'authenticated', user: { username: 'agent1', role: 'AGENT' } }
}

beforeEach(() => {
  vi.clearAllMocks()
  asAdmin()
})

afterEach(() => vi.restoreAllMocks())

describe('CustomerDetailsPage', () => {
  it('shows a loading state', () => {
    mockUseCustomer.mockReturnValue({ customer: null, loading: true, error: null })
    renderPage()
    expect(screen.getByText(/loading customer/i)).toBeInTheDocument()
  })

  it('shows an error / not-found state', () => {
    mockUseCustomer.mockReturnValue({ customer: null, loading: false, error: null })
    renderPage()
    expect(screen.getByText(/customer not found/i)).toBeInTheDocument()
  })

  it('surfaces a fetch error message', () => {
    mockUseCustomer.mockReturnValue({ customer: null, loading: false, error: 'Network error' })
    renderPage()
    expect(screen.getByText(/network error/i)).toBeInTheDocument()
  })

  it('renders the profile when populated', () => {
    mockUseCustomer.mockReturnValue({ customer, loading: false, error: null })
    renderPage()
    expect(screen.getByText('Customer Details')).toBeInTheDocument()
    // Name shows in both the profile header and the Overview row.
    expect(screen.getAllByText('Amina Khan').length).toBeGreaterThan(0)
  })

  // --- issue #42: closing a customer is ADMIN-only -----------------------------------
  describe('close button', () => {
    beforeEach(() => {
      mockUseCustomer.mockReturnValue({ customer, loading: false, error: null })
    })

    it('is shown to an admin', () => {
      asAdmin()
      renderPage()
      expect(screen.getByRole('button', { name: /close/i })).toBeInTheDocument()
    })

    // The point of the issue. An agent must not see it at all — no disabled
    // button and no "access denied" card sitting where a control should be.
    it('is hidden from an agent', () => {
      asAgent()
      renderPage()
      expect(screen.queryByRole('button', { name: /close/i })).not.toBeInTheDocument()
      expect(screen.queryByText(/access denied/i)).not.toBeInTheDocument()
      // The rest of the page still works for them.
      expect(screen.getByRole('button', { name: /edit/i })).toBeInTheDocument()
    })

    it('closes after the confirm is accepted, then returns to the list', async () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true)
      remove.mockResolvedValue(undefined)
      const user = userEvent.setup()
      renderPage()

      await user.click(screen.getByRole('button', { name: /close/i }))

      await waitFor(() => expect(remove).toHaveBeenCalledWith('CUS-1001'))
      expect(navigate).toHaveBeenCalledWith({ name: 'customers' })
    })

    it('does nothing when the confirm is dismissed', async () => {
      vi.spyOn(window, 'confirm').mockReturnValue(false)
      const user = userEvent.setup()
      renderPage()

      await user.click(screen.getByRole('button', { name: /close/i }))

      expect(remove).not.toHaveBeenCalled()
      expect(navigate).not.toHaveBeenCalledWith({ name: 'customers' })
    })

    it('shows an error and stays put when the close fails', async () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true)
      remove.mockRejectedValue(new Error('Boom'))
      const user = userEvent.setup()
      renderPage()

      await user.click(screen.getByRole('button', { name: /close/i }))

      expect(await screen.findByRole('alert')).toHaveTextContent(/could not close customer/i)
      expect(navigate).not.toHaveBeenCalledWith({ name: 'customers' })
    })
  })
})
