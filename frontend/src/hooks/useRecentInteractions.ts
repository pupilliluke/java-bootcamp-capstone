import { useEffect, useState } from 'react'
import { interactionsApi } from '../api/interactions'
import { ApiError } from '../api/ApiError'
import type { Customer, Interaction } from '../types/customer'

// Recent interactions across the whole book, for the dashboard.
//
// This fans out one request per customer, because there is no endpoint that
// reads interactions across customers: InteractionController exposes only
// POST /api/v1/interactions, and reads go through
// GET /api/v1/customers/{id}/interactions. That is fine for a demo book of three
// and wrong for a real one, so the fan-out is capped and the whole hook is a
// stopgap. When the paged GET /api/v1/interactions lands this collapses to a
// single call and the rest of the dashboard does not change.
//
// The cap is on customers, not interactions: MAX_CUSTOMERS requests go out, and
// the merged result is trimmed to `limit`.
const MAX_CUSTOMERS = 12

export function useRecentInteractions(customers: Customer[], limit = 8) {
  const [interactions, setInteractions] = useState<Interaction[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Depend on the ids, not the array. The dashboard rebuilds its customer array
  // on every render, and an array dependency would refetch every time.
  const idKey = customers.map((c) => c.customerId).join(',')

  useEffect(() => {
    const ids = idKey ? idKey.split(',') : []
    if (ids.length === 0) {
      setInteractions([])
      setLoading(false)
      return
    }

    const ctrl = new AbortController()
    setLoading(true)
    setError(null)

    // allSettled, not all: one customer's history failing should thin the feed,
    // not blank it. A single 500 among twelve still leaves eleven useful rows.
    Promise.allSettled(
      ids.slice(0, MAX_CUSTOMERS).map((id) => interactionsApi.list(id, ctrl.signal)),
    )
      .then((results) => {
        if (ctrl.signal.aborted) return
        const merged = results
          .filter((r): r is PromiseFulfilledResult<Interaction[]> => r.status === 'fulfilled')
          .flatMap((r) => r.value)
          .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
          .slice(0, limit)
        setInteractions(merged)

        // Only surface an error when every request failed. A partial failure is
        // reported by the feed being shorter, not by an alert over real rows.
        const allFailed = results.length > 0 && results.every((r) => r.status === 'rejected')
        if (allFailed) {
          const first = results[0]
          const reason = first.status === 'rejected' ? first.reason : null
          if (reason instanceof ApiError && reason.kind === 'abort') return
          setError('Could not load recent activity')
        }
      })
      .finally(() => {
        if (!ctrl.signal.aborted) setLoading(false)
      })

    return () => ctrl.abort()
  }, [idKey, limit])

  return { interactions, loading, error }
}
