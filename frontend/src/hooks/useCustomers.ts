import { useEffect, useState } from 'react'
import { customersApi } from '../api/customers'
import { ApiError } from '../api/ApiError'
import { ALL_CUSTOMER_STATUSES, type Customer, type CustomerStatus } from '../types/customer'

interface Options {
  reloadKey?: number
  statuses?: CustomerStatus[]
  q?: string
  page?: number // zero-based, matching the API
  size?: number
  sort?: string
  direction?: 'asc' | 'desc'
}

// Loads one page of customers.
//
// This used to fetch every customer and hand back an array, which is what let
// the list page slice and count in the browser. That does not survive a real
// book: a thousand customers crossed the wire in full to show eight of them,
// and the pager grew a button per page. The server pages now, so the hook
// returns the rows for the requested page plus the totals the pager needs.
//
// `statuses` still defaults to all four rather than the server's default, which
// is every status except CLOSED. The list page's Closed checkbox has to be able
// to ask for closed customers, and taking the server default would filter them
// out before the checkbox ever saw them.
export function useCustomers({
  reloadKey = 0,
  statuses = ALL_CUSTOMER_STATUSES,
  q,
  page = 0,
  size = 20,
  sort,
  direction,
}: Options = {}) {
  const [customers, setCustomers] = useState<Customer[]>([])
  const [totalElements, setTotalElements] = useState(0)
  const [totalPages, setTotalPages] = useState(1)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // Depend on the joined string, not the array: a caller passing an inline
  // array literal hands in a new reference every render, and as a dependency
  // that is a refetch every render.
  const statusKey = statuses.join(',')

  useEffect(() => {
    const ctrl = new AbortController()
    setLoading(true)
    setError(null)
    customersApi
      .page(
        {
          statuses: statusKey.split(',') as CustomerStatus[],
          q,
          page,
          size,
          sort,
          direction,
        },
        ctrl.signal,
      )
      .then((result) => {
        setCustomers(result.content)
        setTotalElements(result.totalElements)
        setTotalPages(result.totalPages)
      })
      .catch((e) => {
        if (e instanceof ApiError && e.kind === 'abort') return
        setError(e instanceof Error ? e.message : 'Unknown error')
      })
      .finally(() => setLoading(false))
    return () => ctrl.abort()
  }, [reloadKey, statusKey, q, page, size, sort, direction])

  return { customers, totalElements, totalPages, loading, error }
}
