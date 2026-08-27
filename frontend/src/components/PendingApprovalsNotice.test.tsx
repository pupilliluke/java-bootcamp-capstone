import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import AdminOnly from '../auth/AdminOnly'
import PendingApprovalsNotice from './PendingApprovalsNotice'

// The notice is always used behind AdminOnly, so the tests render that exact
// composition -- it is the only way to prove the AGENT case does not fetch.
const { listPending } = vi.hoisted(() => ({ listPending: vi.fn() }))
vi.mock('../api/admin', () => ({ adminApi: { listPending } }))

const auth = vi.hoisted(() => ({
  state: { status: 'authenticated', user: { username: 'admin1', role: 'ADMIN' } } as {
    status: string
    user: { username: string; role: string }
  },
}))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => ({ state: auth.state }) }))

const navigate = vi.fn()
const renderNotice = () =>
  render(
    <AdminOnly fallback={null}>
      <PendingApprovalsNotice navigate={navigate} reloadKey={0} />
    </AdminOnly>,
  )

const pending = [
  { id: 1, username: 'newbie', email: 'newbie@example.com', role: 'AGENT' },
  { id: 2, username: 'other', email: 'other@example.com', role: 'AGENT' },
]

beforeEach(() => {
  vi.clearAllMocks()
  auth.state = { status: 'authenticated', user: { username: 'admin1', role: 'ADMIN' } }
})

describe('PendingApprovalsNotice', () => {
  it('shows the count for an admin with pending accounts and navigates to Settings', async () => {
    listPending.mockResolvedValue(pending)
    renderNotice()
    const notice = await screen.findByRole('button', { name: /waiting for approval/i })
    expect(notice).toHaveTextContent('2 accounts are waiting for approval')
    await userEvent.click(notice)
    expect(navigate).toHaveBeenCalledWith({ name: 'settings' })
  })

  it('names a single waiting account in the singular', async () => {
    listPending.mockResolvedValue([pending[0]])
    renderNotice()
    expect(
      await screen.findByText(/^1 account is waiting for approval$/i),
    ).toBeInTheDocument()
  })

  it('renders nothing for an AGENT and never makes the request', () => {
    auth.state = { status: 'authenticated', user: { username: 'agent1', role: 'AGENT' } }
    const { container } = renderNotice()
    // Behind AdminOnly the component never mounts, so the effect that fetches
    // never runs -- no hasRole('ADMIN') request, no 403 in the console.
    expect(listPending).not.toHaveBeenCalled()
    expect(container).toBeEmptyDOMElement()
  })

  it('renders nothing once the list is empty', async () => {
    listPending.mockResolvedValue([])
    const { container } = renderNotice()
    await waitFor(() => expect(listPending).toHaveBeenCalled())
    expect(screen.queryByText(/waiting for approval/i)).not.toBeInTheDocument()
    expect(container.querySelector('.pending-notice')).toBeNull()
  })

  it('shows an error rather than "0 waiting" when the fetch fails', async () => {
    listPending.mockRejectedValue(new Error('Boom'))
    renderNotice()
    expect(await screen.findByText(/couldn.t check for accounts/i)).toBeInTheDocument()
    // The one thing a failed poll must never render is a count.
    expect(screen.queryByText(/waiting for approval/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/0 account/i)).not.toBeInTheDocument()
  })
})
