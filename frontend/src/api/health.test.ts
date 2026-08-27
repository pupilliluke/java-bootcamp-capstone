import { describe, it, expect, vi, beforeEach } from 'vitest'
import { healthApi } from './health'
import { ApiError } from './ApiError'

// Through the real http() with only fetch mocked — deliberately NOT mocking
// healthApi, because that is how the original bug hid: Actuator answers a
// DOWN system with HTTP 503 and a body that still says which component is
// down, and http() used to throw on any non-2xx, so the panel could never
// show the state its own (module-mocked) tests promised. This seam test
// pins the transport behaviour those tests bypass.
describe('healthApi through the transport', () => {
  beforeEach(() => vi.restoreAllMocks())

  it('resolves a 503 DOWN answer with its component breakdown', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({ status: 'DOWN', components: { db: { status: 'DOWN' } } }),
        { status: 503, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    const health = await healthApi.get()
    expect(health.status).toBe('DOWN')
    expect(health.components?.db.status).toBe('DOWN')
  })

  it('still rejects statuses no health endpoint sends', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response('Bad Gateway', { status: 502 }),
    )

    await expect(healthApi.get()).rejects.toMatchObject({ status: 502 })
  })

  it('rejects a network failure as a network-kind error', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValue(new TypeError('fetch failed'))

    await expect(healthApi.get()).rejects.toSatisfy(
      (e: unknown) => e instanceof ApiError && e.kind === 'network',
    )
  })
})
