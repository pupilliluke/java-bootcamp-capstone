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
    expect(url).toContain('/api/customers/CUS-1001')
  })
})
