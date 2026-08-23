import { useState } from 'react'
import { useAuth } from '../auth/AuthContext'

export default function LoginPage({ expired = false }: { expired?: boolean }) {
  const { login } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (submitting) return
    setSubmitting(true)
    setError('')
    try {
      await login(username, password)
    } catch {
      // Deliberately not echoing the server message: it answers identically for
      // an unknown user and a wrong password so the form cannot be used to
      // enumerate valid usernames.
      setError('Invalid username or password')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main style={styles.page}>
      <h1>Sign in to Northstar CRM</h1>
      {expired && <p role="status">Your session has ended. Sign in again.</p>}

      <form onSubmit={handleSubmit} style={styles.card}>
        <div style={styles.row}>
          <label htmlFor="username">Username</label>
          <input
            id="username"
            name="username"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
          />
        </div>
        <div style={styles.row}>
          <label htmlFor="password">Password</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
        </div>
        {error && (
          <p style={styles.error} role="alert">
            {error}
          </p>
        )}
        <button type="submit" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
      </form>

      <p style={styles.muted}>Demo accounts: agent1 / agent1 · admin1 / admin1</p>
    </main>
  )
}

const styles: Record<string, React.CSSProperties> = {
  page: { fontFamily: 'system-ui, sans-serif', maxWidth: 640, margin: '2rem auto', padding: '0 1rem' },
  card: { border: '1px solid #ccc', borderRadius: 8, padding: '1rem', marginTop: '1rem' },
  row: { display: 'flex', gap: '0.5rem', alignItems: 'center', marginBottom: '0.5rem' },
  muted: { color: '#666', fontSize: '0.9rem' },
  error: { color: '#b00020' },
}
