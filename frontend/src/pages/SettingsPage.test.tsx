import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import SettingsPage from './SettingsPage'

// Mock the admin API and the auth context so each state is deterministic.
const { listPending, enable } = vi.hoisted(() => ({ listPending: vi.fn(), enable: vi.fn() }))
vi.mock('../api/admin', () => ({ adminApi: { listPending, enable } }))

const auth = vi.hoisted(() => ({
  state: { status: 'authenticated', user: { username: 'admin1', role: 'ADMIN' } } as {
    status: string
    user: { username: string; role: string }
  },
}))
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ state: auth.state, login: vi.fn(), logout: vi.fn() }),
}))

beforeEach(() => {
  vi.clearAllMocks()
  auth.state = { status: 'authenticated', user: { username: 'admin1', role: 'ADMIN' } }
})

describe('SettingsPage', () => {
  it('tells non-admins they cannot approve accounts', () => {
    auth.state = { status: 'authenticated', user: { username: 'agent1', role: 'AGENT' } }
    render(<SettingsPage />)
    expect(screen.getByText(/only administrators/i)).toBeInTheDocument()
    expect(listPending).not.toHaveBeenCalled()
  })

  it('shows a loading state while pending accounts load', () => {
    listPending.mockReturnValue(new Promise(() => {}))
    render(<SettingsPage />)
    expect(screen.getByText(/loading pending accounts/i)).toBeInTheDocument()
  })

  it('shows an empty state when nobody is pending', async () => {
    listPending.mockResolvedValue([])
    render(<SettingsPage />)
    expect(await screen.findByText(/no accounts are waiting/i)).toBeInTheDocument()
  })

  it('lists pending accounts with an Approve button', async () => {
    listPending.mockResolvedValue([
      { id: 1, username: 'newbie', email: 'newbie@example.com', role: 'AGENT' },
    ])
    render(<SettingsPage />)
    expect(await screen.findByText('newbie')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /approve/i })).toBeInTheDocument()
  })

  it('surfaces an error when loading fails', async () => {
    listPending.mockRejectedValue(new Error('Boom'))
    render(<SettingsPage />)
    expect(await screen.findByText(/boom/i)).toBeInTheDocument()
  })
})
