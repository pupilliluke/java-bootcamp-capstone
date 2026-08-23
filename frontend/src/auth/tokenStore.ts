export type SessionUser = { username: string; role: string }

// Deliberately in memory, not localStorage: a token in localStorage is readable
// by any script on the page, so an XSS becomes a stolen session. The cost is
// that a refresh signs you out, which is the right trade for a bearer token
// with no revoke endpoint.
let session: { accessToken: string; user: SessionUser } | null = null

export const tokenStore = {
  get: (): string | null => session?.accessToken ?? null,
  getUser: (): SessionUser | null => session?.user ?? null,
  set: (accessToken: string, user: SessionUser) => {
    session = { accessToken, user }
  },
  clear: () => {
    session = null
  },
}
