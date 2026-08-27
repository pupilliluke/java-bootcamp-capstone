import { describe, expect, it, vi, afterEach } from 'vitest'
import { customersApi } from './customers'

afterEach(() => vi.restoreAllMocks())

describe('customersApi', () => {
  it('requests the customer by id', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(
        new Response(JSON.stringify({ customerId: 'CUS-1001' }), { status: 200 }),
      )

    await customersApi.get('CUS-1001')

    expect(fetchMock).toHaveBeenCalledOnce()
    const url = fetchMock.mock.calls[0][0] as string
    expect(url).toContain('/api/v1/customers/CUS-1001')
  })

  // The server treats `status` as repeatable, so several statuses go out as
  // several parameters rather than one comma-joined value — which the backend
  // would read as a single unknown status and answer 400.
  it('repeats the status parameter once per status', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify([]), { status: 200 }))

    await customersApi.list(['ACTIVE', 'CLOSED'])

    const url = fetchMock.mock.calls[0][0] as string
    expect(url).toContain('/api/v1/customers?status=ACTIVE&status=CLOSED')
  })

  // No argument means the server's own default, which is every status except
  // CLOSED. Sending an empty `?` would be the same request with a worse URL.
  it('sends no query string when no status is given', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify([]), { status: 200 }))

    await customersApi.list()

    expect(fetchMock.mock.calls[0][0] as string).toMatch(/\/api\/customers$/)
  })
})
