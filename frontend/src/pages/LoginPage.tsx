import { useState } from 'react'
import { useAuth } from '../auth/AuthContext'
import { authApi } from '../api/auth'
import { ApiError } from '../api/ApiError'

type Mode = 'signin' | 'register'
const MIN_PASSWORD = 12

export default function LoginPage({ expired = false }: { expired?: boolean }) {
  const { login } = useAuth()
  const [mode, setMode] = useState<Mode>('signin')

  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  function switchMode(next: Mode) {
    setMode(next)
    setError('')
    setPassword('')
    setConfirmPassword('')
  }

  async function handleSignIn(e: React.FormEvent) {
    e.preventDefault()
    if (submitting) return
    setSubmitting(true)
    setError('')
    try {
      await login(username, password)
    } catch {
      // Deliberately generic: the server answers identically for an unknown user
      // and a wrong password so the form cannot enumerate valid usernames.
      setError('Invalid username or password')
    } finally {
      setSubmitting(false)
    }
  }

  async function handleRegister(e: React.FormEvent) {
    e.preventDefault()
    if (submitting) return
    if (password.length < MIN_PASSWORD) {
      setError(`Password must be at least ${MIN_PASSWORD} characters`)
      return
    }
    if (password !== confirmPassword) {
      setError('Passwords do not match')
      return
    }
    setSubmitting(true)
    setError('')
    try {
      await authApi.register({ username: username.trim(), email: email.trim(), password })
      // New accounts are created disabled; an ADMIN must approve before sign-in.
      setMode('signin')
      setPassword('')
      setConfirmPassword('')
      setEmail('')
      setNotice('Account created. An administrator needs to approve it before you can sign in.')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create account')
    } finally {
      setSubmitting(false)
    }
  }

  const registerValid =
    !!username.trim() &&
    !!email.trim() &&
    password.length >= MIN_PASSWORD &&
    password === confirmPassword

  return (
    <main className="login-wrap">
      <div className="login-card">
        <div className="login-brand">
          <span className="login-logo">★</span>
          <h1>Northstar CRM</h1>
          <span className="sub">
            {mode === 'signin' ? 'Sign in to your workspace' : 'Create your account'}
          </span>
        </div>

        {expired && mode === 'signin' && (
          <p className="login-expired" role="status">
            Your session has ended. Please sign in again.
          </p>
        )}
        {notice && mode === 'signin' && (
          <p className="success" role="status">
            {notice}
          </p>
        )}

        {mode === 'signin' ? (
          <form onSubmit={handleSignIn}>
            <div className="login-field">
              <label htmlFor="username">Username</label>
              <input
                id="username"
                name="username"
                autoComplete="username"
                placeholder="agent1"
                value={username}
                onChange={(e) => {
                  setUsername(e.target.value)
                  setNotice('')
                }}
              />
            </div>
            <div className="login-field">
              <label htmlFor="password">Password</label>
              <input
                id="password"
                name="password"
                type="password"
                autoComplete="current-password"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
            {error && (
              <p className="error" role="alert">
                {error}
              </p>
            )}
            <button type="submit" className="btn-primary" disabled={submitting}>
              {submitting ? 'Signing in…' : 'Sign in'}
            </button>
          </form>
        ) : (
          <form onSubmit={handleRegister}>
            <div className="login-field">
              <label htmlFor="r-username">Username</label>
              <input
                id="r-username"
                name="username"
                autoComplete="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
              />
            </div>
            <div className="login-field">
              <label htmlFor="r-email">Email</label>
              <input
                id="r-email"
                name="email"
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>
            <div className="login-field">
              <label htmlFor="r-password">Password</label>
              <input
                id="r-password"
                name="password"
                type="password"
                autoComplete="new-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
              <span className="login-help">At least {MIN_PASSWORD} characters.</span>
            </div>
            <div className="login-field">
              <label htmlFor="r-confirm">Confirm password</label>
              <input
                id="r-confirm"
                name="confirmPassword"
                type="password"
                autoComplete="new-password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
              />
            </div>
            {error && (
              <p className="error" role="alert">
                {error}
              </p>
            )}
            <button type="submit" className="btn-primary" disabled={submitting || !registerValid}>
              {submitting ? 'Creating account…' : 'Create account'}
            </button>
          </form>
        )}

        <p className="login-switch">
          {mode === 'signin' ? (
            <>
              New here?{' '}
              <button type="button" className="link-btn" onClick={() => switchMode('register')}>
                Create an account
              </button>
            </>
          ) : (
            <>
              Already have an account?{' '}
              <button type="button" className="link-btn" onClick={() => switchMode('signin')}>
                Sign in
              </button>
            </>
          )}
        </p>

        {mode === 'signin' && (
          <p className="login-hint">
            Demo accounts — <strong>agent1 / agent1</strong> · <strong>admin1 / admin1</strong>
          </p>
        )}
      </div>
    </main>
  )
}
