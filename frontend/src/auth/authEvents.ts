export type AuthEvent = 'expired'

type Listener = (event: AuthEvent) => void

const listeners = new Set<Listener>()

// Lets the http client tell AuthProvider that a token stopped working without
// the api layer importing React or the context importing fetch.
export function onAuthEvent(listener: Listener): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function emitAuthEvent(event: AuthEvent) {
  listeners.forEach((listener) => listener(event))
}
