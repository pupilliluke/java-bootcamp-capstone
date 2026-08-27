import { useEffect, useState } from 'react'
import { customersApi } from '../api/customers'
import { ApiError } from '../api/ApiError'
import type { CustomerStatus } from '../types/customer'

// How many customers hold a status, without fetching any of them.
//
// The dashboard tiles need a number. Counting a page of rows would report the
// size of the page rather than the size of the book, so this asks the server
// for the smallest page it will give and reads the total off it.
export function useCustomerCount(statuses: CustomerStatus[], reloadKey = 0) {
  const [count, setCount] = useState(0)
  const statusKey = statuses.join(',')

  useEffect(() => {
    const ctrl = new AbortController()
    customersApi
      .count(statusKey.split(',') as CustomerStatus[], ctrl.signal)
      .then(setCount)
      .catch((e) => {
        // A failed count leaves the tile at zero rather than breaking the page:
        // the list beside it is the part that matters.
        if (e instanceof ApiError && e.kind === 'abort') return
      })
    return () => ctrl.abort()
  }, [statusKey, reloadKey])

  return count
}
