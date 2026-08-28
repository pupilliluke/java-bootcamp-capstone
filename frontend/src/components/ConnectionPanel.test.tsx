import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import ConnectionPanel from './ConnectionPanel'
import { healthApi } from '../api/health'
import { infoApi } from '../api/info'
import { ApiError } from '../api/ApiError'

vi.mock('../api/health')
vi.mock('../api/info')

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

// The curated identity block ConnectionInfoContributor serves.
const INFO = {
  connections: {
    profile: 'local',
    environment: 'kubernetes: student02',
    database: 'bootcamp',
    schema: 'student02',
    kafka: {
      topic: 'crm.interaction.v1',
      consumerGroup: 'crm-interaction-service-v1',
    },
  },
  runtime: {
    java: { version: '21.0.4', vendor: 'Eclipse Adoptium' },
    dependencies: { springBoot: '3.3.5', hibernate: '6.5.3.Final' },
    os: { name: 'Linux', arch: 'amd64' },
  },
  build: { artifact: 'crm-backend', version: '0.0.1-SNAPSHOT', time: '2026-08-27T12:00:00Z' },
  revision: 'ab0faa7deadbeef1234',
}

const row = (label: string) =>
  screen.getByText(label).nextElementSibling as HTMLElement

describe('ConnectionPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Identity is orthogonal to liveness: most tests get an empty info payload
    // so they assert health behaviour alone, and the identity tests override.
    vi.mocked(infoApi.get).mockResolvedValue({})
  })

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

  // A DOWN backend RESOLVES with the component breakdown: the transport treats
  // Actuator's 503 as data (health.ts treatAsOk), so this fixture is exactly
  // what healthApi.get delivers when the database is down for real. The
  // health.test.ts transport test proves that seam; this proves the rendering.
  it('reports a DOWN backend as down rather than as an error', async () => {
    vi.mocked(healthApi.get).mockResolvedValue({
      status: 'DOWN',
      components: { db: { status: 'DOWN' } },
    })
    render(<ConnectionPanel />)

    await waitFor(() => expect(within(row('Backend')).getByText('DOWN')).toBeInTheDocument())
    expect(within(row('Database')).getByText('DOWN')).toBeInTheDocument()
  })

  // With 503 handled as data, a rejection means the backend genuinely did not
  // answer: network failure (no status), or a status no health endpoint sends.
  it('reports a network failure as unreachable, without inventing a code', async () => {
    vi.mocked(healthApi.get).mockRejectedValue(
      new ApiError('Network error — is the backend running?', 'network'),
    )
    render(<ConnectionPanel />)

    await waitFor(() => expect(screen.getByText(/unreachable/i)).toBeInTheDocument())
    expect(screen.queryByText(/HTTP/)).not.toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent(/network error/i)
  })

  it('shows the status code when an unexpected HTTP failure has one', async () => {
    vi.mocked(healthApi.get).mockRejectedValue(
      new ApiError('Request failed (HTTP 502)', 'http', 502),
    )
    render(<ConnectionPanel />)

    await waitFor(() => expect(screen.getByText(/unreachable/i)).toBeInTheDocument())
    expect(within(row('Backend')).getByText(/HTTP 502/)).toBeInTheDocument()
  })

  // Anonymous callers get UP/DOWN with no components. Behind the login guard
  // this should not happen, but it must not throw if it does.
  it('degrades quietly when the payload carries no components', async () => {
    vi.mocked(healthApi.get).mockResolvedValue({ status: 'UP' })
    render(<ConnectionPanel />)

    await waitFor(() => expect(within(row('Backend')).getByText('UP')).toBeInTheDocument())
    expect(within(row('Database')).getByText(/not reported/i)).toBeInTheDocument()
  })

  // No Kafka health indicator is registered, so the row shows no status at all
  // rather than inventing one or explaining the absence.
  it('shows no Kafka status when the broker has no health indicator', async () => {
    vi.mocked(healthApi.get).mockResolvedValue(HEALTHY)
    render(<ConnectionPanel />)

    await waitFor(() => expect(within(row('Backend')).getByText('UP')).toBeInTheDocument())
    expect(within(row('Kafka')).queryByText(/no health indicator/i)).not.toBeInTheDocument()
    expect(within(row('Kafka')).queryByText(/^(UP|DOWN)$/)).not.toBeInTheDocument()
  })

  it('names the version, environment and database target from info', async () => {
    vi.mocked(healthApi.get).mockResolvedValue(HEALTHY)
    vi.mocked(infoApi.get).mockResolvedValue(INFO)
    render(<ConnectionPanel />)

    await waitFor(() => expect(within(row('Backend')).getByText('UP')).toBeInTheDocument())
    expect(within(row('Backend')).getByText(/v0\.0\.1-SNAPSHOT/)).toBeInTheDocument()
    // The commit revision is deliberately not surfaced in the panel.
    expect(within(row('Backend')).queryByText(/ab0faa7deadb/)).not.toBeInTheDocument()
    // The derived environment, not the profile: "kubernetes: student02" is
    // read from the platform, and the panel never shows an address.
    expect(within(row('Backend')).getByText(/kubernetes: student02/)).toBeInTheDocument()
    expect(within(row('Database')).getByText(/db bootcamp/)).toBeInTheDocument()
    expect(within(row('Database')).getByText(/schema student02/)).toBeInTheDocument()
    expect(screen.queryByText(/localhost:9092|100\.22|5432/)).not.toBeInTheDocument()
    // The in-depth collapsible carries the stack.
    expect(screen.getByText(/21\.0\.4/)).toBeInTheDocument()
    expect(screen.getByText(/springBoot 3\.3\.5/)).toBeInTheDocument()
    expect(within(row('Kafka')).getByText(/crm\.interaction\.v1/)).toBeInTheDocument()
    expect(within(row('Kafka')).getByText(/crm-interaction-service-v1/)).toBeInTheDocument()
  })

  it('shows the derived profile environment on a laptop', async () => {
    vi.mocked(healthApi.get).mockResolvedValue(HEALTHY)
    vi.mocked(infoApi.get).mockResolvedValue({ connections: { environment: 'profile: local' } })
    render(<ConnectionPanel />)

    await waitFor(() => expect(within(row('Backend')).getByText('UP')).toBeInTheDocument())
    expect(within(row('Backend')).getByText(/profile: local/)).toBeInTheDocument()
  })

  // An older backend without the contributor answers info with nothing, or the
  // endpoint 404s. The panel keeps working on health alone.
  it('keeps working when info is unavailable', async () => {
    vi.mocked(healthApi.get).mockResolvedValue(HEALTHY)
    vi.mocked(infoApi.get).mockRejectedValue(new ApiError('Request failed (HTTP 404)', 'http', 404))
    render(<ConnectionPanel />)

    await waitFor(() => expect(within(row('Backend')).getByText('UP')).toBeInTheDocument())
    // The static options section legitimately mentions profiles; the
    // assertion is that the Backend row carries no identity it cannot know.
    expect(within(row('Backend')).queryByText(/profile:|kubernetes/)).not.toBeInTheDocument()
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
