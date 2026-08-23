import { useState } from 'react'
import { customersApi } from '../api/customers'
import { ApiError } from '../api/ApiError'
import type { Customer } from '../types/customer'

// Search-driven version of Lab 35's useCustomers hook: instead of loading on
// mount, it exposes a search(id) function plus loading/error/customer state.
export function useCustomerSearch() {
  const [customer, setCustomer] = useState<Customer | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function search(customerId: string) {
    setLoading(true)
    setError(null)
    setCustomer(null)
    try {
      const found = await customersApi.get(customerId.trim())
      setCustomer(found)
    } catch (e) {
      if (e instanceof ApiError && e.kind === 'abort') return
      setError(e instanceof Error ? e.message : 'Unknown error')
    } finally {
      setLoading(false)
    }
  }

  return { customer, loading, error, search }
}
