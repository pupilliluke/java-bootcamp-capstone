import { afterEach, describe, expect, it, vi } from 'vitest'
import { interactionsApi } from './interactions'

afterEach(() => vi.restoreAllMocks())

describe('interactionsApi', () => {
  it('reads and maps customer interaction history', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify([
          {
            interactionId: 'INT-1001',
            customerId: 'CUS-1001',
            channel: 'EMAIL',
            notes: 'Renewal follow-up',
            occurredAt: '2026-08-24T12:00:00Z',
          },
        ]),
        { status: 200 },
      ),
    )

    const interactions = await interactionsApi.list('CUS / 1001')

    expect(fetchMock).toHaveBeenCalledOnce()
    expect(fetchMock.mock.calls[0][0]).toContain('/api/v1/customers/CUS%20%2F%201001/interactions')
    expect(interactions).toEqual([
      {
        interactionId: 'INT-1001',
        customerId: 'CUS-1001',
        channel: 'EMAIL',
        notes: 'Renewal follow-up',
        createdAt: '2026-08-24T12:00:00Z',
      },
    ])
  })
})
