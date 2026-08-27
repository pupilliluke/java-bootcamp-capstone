import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import ConnectionPanel from './ConnectionPanel'
import { healthApi } from '../api/health'
import { ApiError } from '../api/ApiError'

vi.mock('../api/health')

// The shape Actuator returns to an authorized caller, verified against a
// running backend: components appear only when show-details lets them.
const HEALTHY = {
  status: 'UP' as const,
  groups: ['liveness', 'readiness'],
  components: {
    db: { status: 'UP' as const, details: { database: 'PostgreSQL', validationQuery: 'isValid()' } },
    diskSpace: {
      status: 'UP' as const,
      // The real payload carries the absolute working directory here. It is in
      // this fixture precisely so the assertion below can prove it never
      // reaches the screen.
      details: { path: 'C:\\Users\\someone\\secret-project\\backend\\.', free: 4054962176 },
    },
    ping: { status: 'UP' as const },
  },
}

const row = (label: string) =>
  screen.getByText(label).nextElementSibling as HTMLElement

describe('ConnectionPanel', () => {
  beforeEach(() => vi.clearAllMocks())

  it('shows a loading state before the first check returns', () => {
    vi.mocked(healthApi.get).mockReturnValue(new Promise(() => {}))
    render(<ConnectionPanel />)
    expect(screen.getByText(/checking connection/i)).toBeInTheDocument()
  })

  it('reports the backend and database as up, with the database detail', async () => {
    vi.mocked(healthApi.get).mockResolvedValue(HEALTHY)
    render(<ConnectionPanel />)

    await waitFor(() => expect(within(row('Backend')).getByText('UP')).toBeInTheDocument())
    expect(within(row('Database')).getByText('UP')).toBeInTheDocument()
    expect(screen.getByText(/PostgreSQL/)).toBeInTheDocument()
  })

  // The panel is a diagnostic, not a dump. diskSpace's path is the developer's
  // home directory on a laptop and the container's filesystem in a deployment;
  // neither belongs on screen.
  it('never renders the disk path that rides along in the payload', async () => {
    vi.mocked(healthApi.get).mockResolvedValue(HEALTHY)
    render(<ConnectionPanel />)

    await waitFor(() => expect(within(row('Backend')).getByText('UP')).toBeInTheDocument())
    expect(screen.queryByText(/secret-project/)).not.toBeInTheDocument()
    expect(screen.queryByText(/4054962176/)).not.toBeInTheDocument()
  })

  it('reports a DOWN backend as down rather than as an error', async () => {
    vi.mocked(healthApi.get).mockResolvedValue({
      status: 'DOWN',
      components: { db: { status: 'DOWN' } },
    })
    render(<ConnectionPanel />)

    await waitFor(() => expect(within(row('Backend')).getByText('DOWN')).toBeInTheDocument())
    expect(within(row('Database')).getByText('DOWN')).toBeInTheDocument()
  })

  it('distinguishes unreachable from unhealthy, and shows the status code', async () => {
    vi.mocked(healthApi.get).mockRejectedValue(
      new ApiError('Service Unavailable', 'http', 503),
    )
    render(<ConnectionPanel />)

    await waitFor(() => expect(screen.getByText(/unreachable/i)).toBeInTheDocument())
    expect(screen.getByText(/HTTP 503/)).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('Service Unavailable')
  })

  // Anonymous callers get UP/DOWN with no components. Behind the login guard
  // this should not happen, but it must not throw if it does.
  it('degrades quietly when the payload carries no components', async () => {
    vi.mocked(healthApi.get).mockResolvedValue({ status: 'UP' })
    render(<ConnectionPanel />)

    await waitFor(() => expect(within(row('Backend')).getByText('UP')).toBeInTheDocument())
    expect(within(row('Database')).getByText(/not reported/i)).toBeInTheDocument()
  })

  // Verified against the running backend: no Kafka indicator is registered.
  it('says the broker is not reported rather than inventing a status', async () => {
    vi.mocked(healthApi.get).mockResolvedValue(HEALTHY)
    render(<ConnectionPanel />)

    await waitFor(() => expect(within(row('Backend')).getByText('UP')).toBeInTheDocument())
    expect(within(row('Kafka')).getByText(/no health indicator/i)).toBeInTheDocument()
  })

  it('shows where the UI is served from', async () => {
    vi.mocked(healthApi.get).mockResolvedValue(HEALTHY)
    render(<ConnectionPanel />)

    await waitFor(() => expect(within(row('Backend')).getByText('UP')).toBeInTheDocument())
    expect(within(row('Serving this UI')).getByText(window.location.origin)).toBeInTheDocument()
  })

  it('re-checks on demand', async () => {
    vi.mocked(healthApi.get).mockResolvedValue(HEALTHY)
    const user = userEvent.setup()
    render(<ConnectionPanel />)

    await waitFor(() => expect(healthApi.get).toHaveBeenCalledTimes(1))
    await user.click(screen.getByRole('button', { name: /refresh connection status/i }))
    await waitFor(() => expect(healthApi.get).toHaveBeenCalledTimes(2))
  })

  it('does not poll on its own', async () => {
    vi.useFakeTimers()
    try {
      vi.mocked(healthApi.get).mockResolvedValue(HEALTHY)
      render(<ConnectionPanel />)
      await vi.advanceTimersByTimeAsync(60_000)
      expect(healthApi.get).toHaveBeenCalledTimes(1)
    } finally {
      vi.useRealTimers()
    }
  })
})
