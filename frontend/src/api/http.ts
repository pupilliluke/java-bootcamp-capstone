import { emitAuthEvent } from '../auth/authEvents'
import { tokenStore } from '../auth/tokenStore'
import { ApiError } from './ApiError'

// Base URL from env (Lab 35 pattern). Empty => relative paths, which the Vite
// dev proxy forwards to the backend on :8080 (see vite.config.ts).
const baseUrl = import.meta.env.VITE_API_BASE_URL || ''

export type RequestOptions = { intercept401?: boolean }

// One place that does fetch, sets headers, attaches the bearer token, and maps
// every failure to ApiError. Components never call fetch directly.
export async function http<T>(
  path: string,
  init: RequestInit = {},
  signal?: AbortSignal,
  options: RequestOptions = {},
): Promise<T> {
  const { intercept401 = true } = options

  const headers = new Headers(init.headers)
  if (!headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (!headers.has('X-Correlation-ID')) headers.set('X-Correlation-ID', 'lab-request-001')

  // Only relative paths carry the token. An absolute URL points somewhere we do
  // not control, and a bearer token must never be sent there.
  const token = tokenStore.get()
  if (token && !/^https?:\/\//i.test(path)) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  let res: Response
  try {
    res = await fetch(`${baseUrl}${path}`, { ...init, signal, headers })
  } catch (e) {
    if (e instanceof Error && e.name === 'AbortError') {
      throw new ApiError('Request aborted', 'abort')
    }
    throw new ApiError('Network error — is the backend running?', 'network')
  }

  // The token is missing, malformed or past its exp claim. Dropping it here and
  // announcing it lets AuthProvider fall back to the login screen from anywhere
  // in the app, rather than each caller handling 401 on its own.
  if (res.status === 401 && intercept401) {
    tokenStore.clear()
    emitAuthEvent('expired')
    throw new ApiError('Your session has ended. Sign in again.', 'http', 401)
  }

  if (!res.ok) {
    throw new ApiError(await errorMessage(res), 'http', res.status)
  }

  if (res.status === 204) return null as T
  try {
    return (await res.json()) as T
  } catch {
    throw new ApiError('Could not parse response', 'parse')
  }
}

// GlobalExceptionHandler returns {timestamp, status, error, message} on every
// failure path, including the assembled "Validation failed: ..." string. Without
// this the user only ever sees the status line and the field errors are lost.
async function errorMessage(res: Response): Promise<string> {
  try {
    const body = await res.json()
    if (body && typeof body.message === 'string') return body.message
  } catch {
    // no JSON body — fall through to the status line
  }
  return `Request failed (HTTP ${res.status})`
}
