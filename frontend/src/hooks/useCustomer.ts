import { useEffect, useState } from 'react'
import { customersApi } from '../api/customers'
import { ApiError } from '../api/ApiError'
import type { Customer } from '../types/customer'

// Loads a single customer by id (GET /api/v1/customers/{id}).
export function useCustomer(customerId: string) {
  const [customer, setCustomer] = useState<Customer | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const ctrl = new AbortController()
    setLoading(true)
    setError(null)
    setCustomer(null)
    customersApi
      .get(customerId, ctrl.signal)
      .then(setCustomer)
      .catch((e) => {
        if (e instanceof ApiError && e.kind === 'abort') return
        setError(e instanceof Error ? e.message : 'Unknown error')
      })
      .finally(() => setLoading(false))
    return () => ctrl.abort()
  }, [customerId])

  return { customer, loading, error }
}
