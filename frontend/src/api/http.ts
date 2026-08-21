import { ApiError } from './ApiError'

// Base URL from env (Lab 35 pattern). Empty => relative paths, which the Vite
// dev proxy forwards to the backend on :8080 (see vite.config.ts).
const baseUrl = import.meta.env.VITE_API_BASE_URL || ''

// One place that does fetch, sets headers, and maps every failure to ApiError.
// Components never call fetch directly.
export async function http<T>(
  path: string,
  init: RequestInit = {},
  signal?: AbortSignal,
): Promise<T> {
  let res: Response
  try {
    res = await fetch(`${baseUrl}${path}`, {
      ...init,
      signal,
      headers: {
        'Content-Type': 'application/json',
        'X-Correlation-ID': 'lab-request-001',
        ...(init.headers ?? {}),
      },
    })
  } catch (e) {
    if (e instanceof Error && e.name === 'AbortError') {
      throw new ApiError('Request aborted', 'abort')
    }
    throw new ApiError('Network error — is the backend running?', 'network')
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
