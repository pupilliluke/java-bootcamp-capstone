import { createContext, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { authApi } from '../api/auth'
import { onAuthEvent } from './authEvents'
import { tokenStore } from './tokenStore'
import type { SessionUser } from './tokenStore'

export type AuthState =
  | { status: 'checking' }
  | { status: 'anonymous'; expired?: boolean }
  | { status: 'authenticated'; user: SessionUser }

interface AuthValue {
  state: AuthState
  login: (username: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'checking' })

  useEffect(() => {
    const user = tokenStore.getUser()
    setState(user ? { status: 'authenticated', user } : { status: 'anonymous' })
  }, [])

  useEffect(
    () =>
      onAuthEvent((event) => {
        if (event === 'expired') {
          tokenStore.clear()
          setState({ status: 'anonymous', expired: true })
        }
      }),
    [],
  )

  const value = useMemo<AuthValue>(
    () => ({
      state,
      login: async (username: string, password: string) => {
        const session = await authApi.login(username, password)
        tokenStore.set(session.accessToken, {
          username: session.username,
          role: session.role,
        })
        setState({
          status: 'authenticated',
          user: { username: session.username, role: session.role },
        })
      },
      logout: () => {
        // Stateless bearer tokens and no revoke endpoint on the API, so logout
        // is local: discard the token and let the workspace unmount with it.
        tokenStore.clear()
        setState({ status: 'anonymous' })
      },
    }),
    [state],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth requires AuthProvider')
  return ctx
}
