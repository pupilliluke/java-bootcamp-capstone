import { http } from './http'

// A pending (disabled) account awaiting ADMIN approval — issue #16.
export interface PendingUser {
  id: number
  username: string
  email: string
  role: string
  createdAt?: string
}

export const adminApi = {
  // NOTE: issue #16 explicitly scopes POST /register and PATCH .../enable, but
  // the Settings "pending accounts" list also needs a GET. Coding the frontend
  // against this contract; the backend owner needs to add the endpoint.
  listPending(signal?: AbortSignal): Promise<PendingUser[]> {
    return http<PendingUser[]>('/api/admin/users/pending', {}, signal)
  },

  // PATCH /api/admin/users/{id}/enable — ADMIN only (403 for AGENT).
  enable(id: number, signal?: AbortSignal): Promise<unknown> {
    return http<unknown>(`/api/admin/users/${id}/enable`, { method: 'PATCH' }, signal)
  },
}
