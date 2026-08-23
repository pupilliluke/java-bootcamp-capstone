import { http } from './http'

// Mirrors LoginResponseDTO on the Spring side.
export interface LoginResponse {
  accessToken: string
  tokenType: string
  username: string
  role: string
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
}
