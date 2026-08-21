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

export type Channel = 'PHONE' | 'EMAIL' | 'CHAT'

export interface CreateInteraction {
  channel: Channel
  summary: string
}

export interface Interaction extends CreateInteraction {
  createdAt: string
}
