import type { CreateCustomer, Customer, CustomerStatus, CustomerUpdate } from '../types/customer'
import type { PageResponse } from '../types/customer'
import { http } from './http'

export interface ListOptions {
  statuses?: CustomerStatus[]
  q?: string
  page?: number
  size?: number
  sort?: string
  direction?: 'asc' | 'desc'
}

// Matches the Lab 49 backend contract (CustomerController).
export const customersApi = {
  // GET /api/customers is paged: the response is a PageResponse, not an array,
  // and the server caps size at 100 whatever is asked for.
  page(opts: ListOptions = {}, signal?: AbortSignal): Promise<PageResponse<Customer>> {
    const params = new URLSearchParams()
    // Repeatable server-side: ?status=ACTIVE&status=PROSPECT asks for both.
    // Passing none takes the server default, every status except CLOSED.
    opts.statuses?.forEach((s) => params.append('status', s))
    if (opts.q) params.set('q', opts.q)
    if (opts.page !== undefined) params.set('page', String(opts.page))
    if (opts.size !== undefined) params.set('size', String(opts.size))
    if (opts.sort) params.set('sort', opts.sort)
    if (opts.direction) params.set('direction', opts.direction)
    const query = params.toString()
    return http<PageResponse<Customer>>(`/api/customers${query ? `?${query}` : ''}`, {}, signal)
  },

  // How many customers match a filter, without pulling any of them back. Asks
  // for the smallest page the server will give and reads the total off it,
  // which is what the dashboard tiles need: a number, not a list.
  async count(statuses?: CustomerStatus[], signal?: AbortSignal): Promise<number> {
    const page = await customersApi.page({ statuses, page: 0, size: 1 }, signal)
    return page.totalElements
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
