import { useEffect, useRef } from 'react'
import { GOOGLE_CLIENT_ID } from '../config'

// Renders the official "Sign in with Google" button once the GIS script has
// loaded, and hands the returned ID token back through onCredential. The script
// is loaded async in index.html, so it may not be ready at mount; we poll for a
// short window rather than assume it, and give up quietly if it never arrives
// (e.g. offline) so the password form is still usable.
export default function GoogleSignInButton({
  onCredential,
  onError,
}: {
  onCredential: (idToken: string) => void
  onError?: (message: string) => void
}) {
  const containerRef = useRef<HTMLDivElement>(null)
  // Hold the latest callbacks in refs so re-renders don't re-initialize GIS.
  const onCredentialRef = useRef(onCredential)
  onCredentialRef.current = onCredential
  const onErrorRef = useRef(onError)
  onErrorRef.current = onError

  useEffect(() => {
    let cancelled = false
    let attempts = 0

    function render() {
      if (cancelled) return
      const google = window.google
      if (!google || !containerRef.current) {
        // ~5s of 100ms polls before giving up.
        if (attempts++ > 50) {
          onErrorRef.current?.('Google Sign-In is unavailable right now.')
          return
        }
        window.setTimeout(render, 100)
        return
      }

      google.accounts.id.initialize({
        client_id: GOOGLE_CLIENT_ID,
        callback: (response) => onCredentialRef.current(response.credential),
        cancel_on_tap_outside: true,
      })
      containerRef.current.innerHTML = ''
      google.accounts.id.renderButton(containerRef.current, {
        type: 'standard',
        theme: 'outline',
        size: 'large',
        text: 'continue_with',
        shape: 'pill',
        logo_alignment: 'left',
        width: 280,
      })
    }

    render()
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div
      className="google-btn"
      ref={containerRef}
      aria-label="Sign in with Google"
      style={{ display: 'flex', justifyContent: 'center', minHeight: 44 }}
    />
  )
}
