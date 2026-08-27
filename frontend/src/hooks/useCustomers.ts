import { useEffect, useState } from 'react'
import { customersApi } from '../api/customers'
import { ApiError } from '../api/ApiError'
import { ALL_CUSTOMER_STATUSES, type Customer, type CustomerStatus } from '../types/customer'

// Loads the customer list on mount (Lab 35 useCustomers pattern).
// `reloadKey` lets callers force a refetch after a create.
//
// `statuses` defaults to all four rather than to the server's default, which is
// every status except CLOSED. Both callers — the list page and the dashboard —
// still filter, count and page over the whole array client-side, so taking the
// server default would drop closed customers out from under them: the list
// page's Closed checkbox would filter an array that never contains one, and the
// dashboard's Total Customers tile would quietly stop counting them.
//
// Narrowing this to what each page actually needs is the follow-up to issue #48
// and a change to how the list page pages, not something to slip in here.
export function useCustomers(reloadKey = 0, statuses: CustomerStatus[] = ALL_CUSTOMER_STATUSES) {
  const [customers, setCustomers] = useState<Customer[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // The effect depends on the joined string, not the array. A caller passing an
  // inline array literal hands in a new reference on every render, and as a
  // dependency that is a refetch on every render.
  const statusKey = statuses.join(',')

  useEffect(() => {
    const ctrl = new AbortController()
    setLoading(true)
    setError(null)
    customersApi
      .list(statusKey.split(',') as CustomerStatus[], ctrl.signal)
      .then(setCustomers)
      .catch((e) => {
        if (e instanceof ApiError && e.kind === 'abort') return
        setError(e instanceof Error ? e.message : 'Unknown error')
      })
      .finally(() => setLoading(false))
    return () => ctrl.abort()
  }, [reloadKey, statusKey])

  return { customers, loading, error }
}
