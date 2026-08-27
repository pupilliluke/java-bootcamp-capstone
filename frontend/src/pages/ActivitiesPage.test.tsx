import { render, screen, waitFor } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import ActivitiesPage from './ActivitiesPage'
import { customersApi } from '../api/customers'
import { interactionsApi } from '../api/interactions'

// ActivitiesPage reads real interactions across customers. It used to render a
// fixed demo array, so these tests cover the states that array could never
// reach: loading, empty, populated, and a failure.
vi.mock('../api/customers')
vi.mock('../api/interactions')

const CUSTOMERS = [
  { customerId: 'CUS-1001', fullName: 'Amina Khan', email: 'amina.khan@example.test', phone: undefined, status: 'ACTIVE' as const, createdAt: '2026-08-01T10:00:00Z' },
  { customerId: 'CUS-1002', fullName: 'Ravi Singh', email: 'ravi.singh@example.test', phone: undefined, status: 'PROSPECT' as const, createdAt: '2026-08-02T10:00:00Z' },
]

const AMINA_INTERACTION = {
  interactionId: 'INT-aaa',
  customerId: 'CUS-1001',
  channel: 'PHONE' as const,
  notes: 'Renewal call',
  createdAt: '2026-08-27T10:00:00Z',
}

const RAVI_INTERACTION = {
  interactionId: 'INT-bbb',
  customerId: 'CUS-1002',
  channel: 'EMAIL' as const,
  notes: 'Sent onboarding pack',
  createdAt: '2026-08-27T12:00:00Z',
}

describe('ActivitiesPage', () => {
  beforeEach(() => {
    vi.mocked(customersApi.list).mockResolvedValue(CUSTOMERS)
    vi.mocked(interactionsApi.list).mockImplementation(async (id: string) =>
      id === 'CUS-1001' ? [AMINA_INTERACTION] : [RAVI_INTERACTION],
    )
  })

  it('shows a loading state before the feed arrives', () => {
    render(<ActivitiesPage navigate={vi.fn()} />)
    expect(screen.getByText(/loading activity/i)).toBeInTheDocument()
  })

  it('merges interactions across customers, newest first, naming each customer', async () => {
    render(<ActivitiesPage navigate={vi.fn()} />)

    await waitFor(() => expect(screen.getByText('Sent onboarding pack')).toBeInTheDocument())
    expect(screen.getByText('Renewal call')).toBeInTheDocument()
    expect(screen.getByText('Amina Khan')).toBeInTheDocument()
    expect(screen.getByText('Ravi Singh')).toBeInTheDocument()

    // Ravi's is the later timestamp, so it leads the feed.
    const notes = screen.getAllByText(/Renewal call|Sent onboarding pack/)
    expect(notes[0]).toHaveTextContent('Sent onboarding pack')
  })

  it('opens the customer behind an interaction', async () => {
    const navigate = vi.fn()
    render(<ActivitiesPage navigate={navigate} />)

    await waitFor(() => expect(screen.getByText('Renewal call')).toBeInTheDocument())
    screen.getByText('Renewal call').closest('.tl-body')!.dispatchEvent(
      new MouseEvent('click', { bubbles: true }),
    )
    expect(navigate).toHaveBeenCalledWith({ name: 'details', customerId: 'CUS-1001' })
  })

  it('reports an empty book rather than an empty page', async () => {
    vi.mocked(customersApi.list).mockResolvedValue([])
    render(<ActivitiesPage navigate={vi.fn()} />)
    await waitFor(() => expect(screen.getByText(/no interactions recorded yet/i)).toBeInTheDocument())
  })

  it('surfaces a failure to load customers', async () => {
    vi.mocked(customersApi.list).mockRejectedValue(new Error('backend down'))
    render(<ActivitiesPage navigate={vi.fn()} />)
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('backend down'))
  })
})
