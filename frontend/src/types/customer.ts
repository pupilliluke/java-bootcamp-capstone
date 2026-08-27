// Matches the Lab 49 backend contract (CustomerResponseDTO + CustomerStatus).
export type CustomerStatus = 'ACTIVE' | 'SUSPENDED' | 'PROSPECT' | 'CLOSED'

// GET /api/customers answers with every status except CLOSED when asked for no
// status in particular. A caller that wants closed customers too — anything
// still filtering or counting client-side — has to say so, and this is the list
// it says it with.
export const ALL_CUSTOMER_STATUSES: CustomerStatus[] = ['ACTIVE', 'SUSPENDED', 'PROSPECT', 'CLOSED']

export interface Customer {
  customerId: string
  fullName: string
  email: string
  phone?: string
  status: CustomerStatus
  createdAt?: string
}

// POST /api/customers body (CustomerRequestDTO). No customerId, the server
// assigns it and returns it on the created Customer.
export interface CreateCustomer {
  fullName: string
  email: string
  phone?: string
  status: CustomerStatus
}
// PUT /api/customers/{id} body (CustomerUpdateDTO)
export interface CustomerUpdate {
  fullName: string
  email: string
  phone?: string
  status: CustomerStatus
}

export type Channel = 'PHONE' | 'EMAIL' | 'CHAT'

// POST /api/interactions body (CreateInteractionRequest): customerId + channel + notes.
export interface CreateInteraction {
  customerId: string
  channel: Channel
  notes: string
}

export interface Interaction {
  interactionId: string
  customerId: string
  channel: Channel
  notes: string
  createdAt: string
}

// ---- mock-only shapes (no backend endpoint exists) ----
export interface Contact {
  name: string
  designation: string
  email: string
  phone: string
}

