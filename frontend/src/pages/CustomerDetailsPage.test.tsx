import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import CustomerDetailsPage from './CustomerDetailsPage'
import { useCustomer } from '../hooks/useCustomer'
import { interactionsApi } from '../api/interactions'

vi.mock('../hooks/useCustomer')
vi.mock('../api/interactions')
const mockUseCustomer = vi.mocked(useCustomer)
const mockInteractionsApi = vi.mocked(interactionsApi)

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
  vi.clearAllMocks()
  // Most render-state tests do not exercise interaction history. Keeping this
  // request pending prevents an unrelated async state update after their sync
  // assertions; history-specific tests override it below.
  mockInteractionsApi.list.mockImplementation(
    () => new Promise<Awaited<ReturnType<typeof interactionsApi.list>>>(() => {}),
  )
})

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
})
