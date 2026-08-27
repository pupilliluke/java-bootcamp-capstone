import { useEffect, useRef } from 'react'
import { GOOGLE_CLIENT_ID } from '../config'

const GSI_SRC = 'https://accounts.google.com/gsi/client'

// Inject the Google Identity Services script on demand and resolve once it is
// ready. Loading it here rather than from a static tag in index.html means a
// build with Google Sign-In disabled (e.g. the e2e run, which never mounts this
// component) never fetches the third-party script at all.
function loadGsiScript(): Promise<void> {
  return new Promise((resolve, reject) => {
    if (window.google?.accounts?.id) {
      resolve()
      return
    }
    let script = document.querySelector<HTMLScriptElement>(`script[src="${GSI_SRC}"]`)
    if (!script) {
      script = document.createElement('script')
      script.src = GSI_SRC
      script.async = true
      document.head.appendChild(script)
    }
    script.addEventListener('load', () => resolve(), { once: true })
    script.addEventListener('error', () => reject(new Error('Google script failed to load')), {
      once: true,
    })
  })
}

// Renders the official "Sign in with Google" button once GIS is ready, and hands
// the returned ID token back through onCredential. Gives up quietly if the script
// cannot load (e.g. offline) so the password form is still usable.
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

    function render() {
      if (cancelled) return
      const google = window.google
      if (!google || !containerRef.current) {
        onErrorRef.current?.('Google Sign-In is unavailable right now.')
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

    loadGsiScript()
      .then(render)
      .catch(() => onErrorRef.current?.('Google Sign-In is unavailable right now.'))

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
