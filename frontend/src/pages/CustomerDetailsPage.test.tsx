import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import CustomerDetailsPage from './CustomerDetailsPage'
import { useCustomer } from '../hooks/useCustomer'
import { interactionsApi } from '../api/interactions'
import { customersApi } from '../api/customers'

vi.mock('../hooks/useCustomer')
vi.mock('../api/interactions')
// Automock rather than a hand-written factory: a factory returning only
// { customersApi: { remove } } would make every other method undefined, so the
// first test that reaches customersApi.list fails inside a hook instead of here.
vi.mock('../api/customers')
const mockUseCustomer = vi.mocked(useCustomer)
const mockInteractionsApi = vi.mocked(interactionsApi)
const mockCustomersApi = vi.mocked(customersApi)

// The page reads the signed-in role to decide whether to offer the close button.
const auth = vi.hoisted(() => ({
  state: { status: 'authenticated', user: { username: 'admin1', role: 'ADMIN' } } as {
    status: string
    user: { username: string; role: string }
  },
}))
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ state: auth.state, login: vi.fn(), logout: vi.fn() }),
}))
const asAdmin = () => {
  auth.state = { status: 'authenticated', user: { username: 'admin1', role: 'ADMIN' } }
}
const asAgent = () => {
  auth.state = { status: 'authenticated', user: { username: 'agent1', role: 'AGENT' } }
}

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

beforeEach(() => {
  // resetAllMocks, not clearAllMocks: clear only wipes call history, so an
  // implementation set by one test (a rejecting customersApi.remove, say)
  // survives into every test declared after it and fails it for an invisible
  // reason. reset drops implementations too, which is why each block below
  // sets up its own.
  vi.resetAllMocks()
  asAdmin()
  // Most render-state tests do not exercise interaction history. Keeping this
  // request pending prevents an unrelated async state update after their sync
  // assertions; history-specific tests override it below.
  mockInteractionsApi.list.mockImplementation(
    () => new Promise<Awaited<ReturnType<typeof interactionsApi.list>>>(() => {}),
  )
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

  it('renders server-backed interaction history', async () => {
    mockUseCustomer.mockReturnValue({ customer, loading: false, error: null })
    mockInteractionsApi.list.mockResolvedValue([
      {
        interactionId: 'INT-1001',
        customerId: 'CUS-1001',
        channel: 'EMAIL',
        notes: 'Renewal follow-up',
        createdAt: '2026-08-24T12:00:00Z',
      },
    ])

    renderPage()
    await userEvent.click(screen.getByRole('button', { name: 'Activities' }))

    expect(await screen.findByText('Renewal follow-up')).toBeInTheDocument()
    expect(mockInteractionsApi.list).toHaveBeenCalledWith(
      'CUS-1001',
      expect.any(AbortSignal),
    )
  })

  it('adds a successfully saved interaction to the history', async () => {
    mockUseCustomer.mockReturnValue({ customer, loading: false, error: null })
    mockInteractionsApi.list.mockResolvedValue([])
    mockInteractionsApi.create.mockResolvedValue({
      interactionId: 'INT-1002',
      customerId: 'CUS-1001',
      channel: 'CHAT',
      notes: 'Discussed upgrade',
      createdAt: '2026-08-24T12:30:00Z',
    })

    renderPage()
    await userEvent.click(screen.getByRole('button', { name: 'Activities' }))
    await userEvent.selectOptions(screen.getByLabelText('Channel'), 'CHAT')
    await userEvent.type(screen.getByLabelText('Notes'), 'Discussed upgrade')
    await userEvent.click(screen.getByRole('button', { name: 'Add Activity' }))

    expect(await screen.findByText('Discussed upgrade')).toBeInTheDocument()
    expect(mockInteractionsApi.create).toHaveBeenCalledWith({
      customerId: 'CUS-1001',
      channel: 'CHAT',
      notes: 'Discussed upgrade',
    })
  })

  // --- issue #42: closing a customer is ADMIN-only ---------------------------
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
      mockCustomersApi.remove.mockResolvedValue(undefined)
      const user = userEvent.setup()
      renderPage()

      await user.click(screen.getByRole('button', { name: /close/i }))

      await waitFor(() => expect(mockCustomersApi.remove).toHaveBeenCalledWith('CUS-1001'))
      expect(navigate).toHaveBeenCalledWith({ name: 'customers' })
    })

    it('does nothing when the confirm is dismissed', async () => {
      vi.spyOn(window, 'confirm').mockReturnValue(false)
      const user = userEvent.setup()
      renderPage()

      await user.click(screen.getByRole('button', { name: /close/i }))

      expect(mockCustomersApi.remove).not.toHaveBeenCalled()
      expect(navigate).not.toHaveBeenCalledWith({ name: 'customers' })
    })

    it('shows an error and stays put when the close fails', async () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true)
      mockCustomersApi.remove.mockRejectedValue(new Error('Boom'))
      const user = userEvent.setup()
      renderPage()

      await user.click(screen.getByRole('button', { name: /close/i }))

      expect(await screen.findByRole('alert')).toHaveTextContent(/could not close customer/i)
      expect(navigate).not.toHaveBeenCalledWith({ name: 'customers' })
    })

    // The button re-enables on the way out rather than relying on the page
    // being unmounted by the navigation.
    it('leaves the button enabled after a successful close', async () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true)
      mockCustomersApi.remove.mockResolvedValue(undefined)
      const user = userEvent.setup()
      renderPage()

      await user.click(screen.getByRole('button', { name: /close/i }))

      await waitFor(() => expect(navigate).toHaveBeenCalledWith({ name: 'customers' }))
      expect(screen.getByRole('button', { name: /^close$/i })).toBeEnabled()
    })
  })
})
