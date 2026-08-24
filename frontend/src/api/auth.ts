import { http } from './http'
import type { UserRole } from '../types/user'

// Mirrors LoginResponseDTO on the Spring side.
export interface LoginResponse {
  accessToken: string
  tokenType: string
  username: string
  role: UserRole
}

// POST /api/auth/register body (RegisterRequest). Role is assigned server-side
// (AGENT, enabled=false); the client never sends one.
export interface RegisterRequest {
  username: string
  email: string
  password: string
}

export const authApi = {
  // intercept401 is off here: a wrong password is a failed login, not an
  // expired session, and firing the 'expired' event would bounce the user
  // through the "your session has ended" path instead of showing the error.
  login(username: string, password: string): Promise<LoginResponse> {
    return http<LoginResponse>(
      '/api/auth/login',
      { method: 'POST', body: JSON.stringify({ username, password }) },
      undefined,
      { intercept401: false },
    )
  },

  // Public sign-up (issue #16). 201 on success; the new account is disabled
  // until an ADMIN approves it. intercept401 off: a duplicate/validation error
  // is a failed registration, not an expired session.
  register(body: RegisterRequest): Promise<unknown> {
    return http<unknown>(
      '/api/auth/register',
      { method: 'POST', body: JSON.stringify(body) },
      undefined,
      { intercept401: false },
    )
  },
}
