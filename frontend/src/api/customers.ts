import type { CreateCustomer, Customer } from '../types/customer'
import { http } from './http'

// Matches the Lab 49 backend contract (CustomerController).
export const customersApi = {
  list(signal?: AbortSignal): Promise<Customer[]> {
    return http<Customer[]>('/api/customers', {}, signal)
  },
  get(customerId: string, signal?: AbortSignal): Promise<Customer> {
    return http<Customer>(`/api/customers/${encodeURIComponent(customerId)}`, {}, signal)
  },
  create(body: CreateCustomer, signal?: AbortSignal): Promise<Customer> {
    return http<Customer>('/api/customers', { method: 'POST', body: JSON.stringify(body) }, signal)
  },
}
