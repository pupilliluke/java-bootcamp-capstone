import { useEffect, useState } from 'react'
import { customersApi } from '../api/customers'
import { ApiError } from '../api/ApiError'
import type { Customer } from '../types/customer'

// Loads the full customer list on mount (Lab 35 useCustomers pattern).
// `reloadKey` lets callers force a refetch after a create.
export function useCustomers(reloadKey = 0) {
  const [customers, setCustomers] = useState<Customer[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const ctrl = new AbortController()
    setLoading(true)
    setError(null)
    customersApi
      .list(ctrl.signal)
      .then(setCustomers)
      .catch((e) => {
        if (e instanceof ApiError && e.kind === 'abort') return
        setError(e instanceof Error ? e.message : 'Unknown error')
      })
      .finally(() => setLoading(false))
    return () => ctrl.abort()
  }, [reloadKey])

  return { customers, loading, error }
}
