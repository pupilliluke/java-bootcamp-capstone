import type { CreateCustomer, Customer, CustomerStatus, CustomerUpdate } from '../types/customer'
import { http } from './http'

// Matches the Lab 49 backend contract (CustomerController).
export const customersApi = {
  // `status` is repeatable server-side: ?status=ACTIVE&status=PROSPECT asks for
  // both. Passing nothing takes the server's default, which is every status
  // except CLOSED — so a caller that needs closed customers has to name them.
  list(statuses?: CustomerStatus[], signal?: AbortSignal): Promise<Customer[]> {
    const query = statuses?.length
      ? `?${statuses.map((s) => `status=${encodeURIComponent(s)}`).join('&')}`
      : ''
    return http<Customer[]>(`/api/customers${query}`, {}, signal)
  },
  get(customerId: string, signal?: AbortSignal): Promise<Customer> {
    return http<Customer>(`/api/customers/${encodeURIComponent(customerId)}`, {}, signal)
  },
  create(body: CreateCustomer, signal?: AbortSignal): Promise<Customer> {
    return http<Customer>('/api/customers', { method: 'POST', body: JSON.stringify(body) }, signal)
  },
  update(customerId: string, body: CustomerUpdate, signal?: AbortSignal): Promise<Customer> {
    return http<Customer>(
        `/api/customers/${encodeURIComponent(customerId)}`,
        { method: 'PUT', body: JSON.stringify(body) },
        signal,
    )
  },
  // Soft delete, probably will stay this way
  remove(customerId: string, signal?: AbortSignal): Promise<void> {
    return http<void>(`/api/customers/${encodeURIComponent(customerId)}`, { method: 'DELETE' }, signal)
  },
}
