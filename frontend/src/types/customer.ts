// Matches the Lab 49 backend contract (CustomerResponseDTO + CustomerStatus).
export type CustomerStatus = 'ACTIVE' | 'SUSPENDED' | 'PROSPECT' | 'CLOSED'

export interface Customer {
  customerId: string
  fullName: string
  email: string
  phone?: string
  status: CustomerStatus
  createdAt?: string
}

// POST /api/customers body (CustomerRequestDTO).
export interface CreateCustomer {
  customerId: string
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

// 202 response (InteractionEvent) — plus a local `createdAt` for the timeline,
// since the backend is fire-and-forget over Kafka and offers no GET to list.
export interface Interaction {
  channel: Channel
  notes: string
  createdAt: string
  interactionId?: string
}

// ---- mock-only shapes (no backend endpoint exists) ----
export interface Contact {
  name: string
  designation: string
  email: string
  phone: string
}

export interface Activity {
  date: string
  type: string
  subject: string
  assignedTo: string
  status: 'Completed' | 'Pending'
}
