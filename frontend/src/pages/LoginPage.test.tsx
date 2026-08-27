import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, it, expect, vi } from 'vitest'
import LoginPage from './LoginPage'

// useAuth is the only dependency; stub it so no real auth/network runs.
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({
    login: vi.fn(),
    loginWithGoogle: vi.fn(),
    logout: vi.fn(),
    state: { status: 'anonymous' },
  }),
}))

describe('LoginPage', () => {
  it('renders the sign-in form by default', () => {
    render(<LoginPage />)
    expect(screen.getByText('Sign in to your workspace')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^sign in$/i })).toBeInTheDocument()
  })

  it('shows the session-expired notice', () => {
    render(<LoginPage expired />)
    expect(screen.getByText(/your session has ended/i)).toBeInTheDocument()
  })

  it('offers Google sign-in on the sign-in form', () => {
    render(<LoginPage />)
    // The GIS script never loads under jsdom, so the button stays an empty
    // container; its presence is what we assert here.
    expect(screen.getByLabelText('Sign in with Google')).toBeInTheDocument()
  })

  it('does not offer Google sign-in on the create-account form', async () => {
    const user = userEvent.setup()
    render(<LoginPage />)
    await user.click(screen.getByRole('button', { name: /create an account/i }))
    expect(screen.queryByLabelText('Sign in with Google')).not.toBeInTheDocument()
  })

  it('switches to the create-account form with a confirm-password field', async () => {
    const user = userEvent.setup()
    render(<LoginPage />)
    await user.click(screen.getByRole('button', { name: /create an account/i }))
    expect(screen.getByText('Create your account')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toBeInTheDocument()
    expect(screen.getByLabelText('Confirm password')).toBeInTheDocument()
    // Disabled until username + email + 12-char matching passwords are present.
    expect(screen.getByRole('button', { name: 'Create account' })).toBeDisabled()
  })
})

describe('LoginPage with Google Sign-In disabled', () => {
  afterEach(() => {
    vi.unstubAllEnvs()
    vi.resetModules()
  })

  it('omits the Google button entirely when VITE_ENABLE_GSI is false', async () => {
    // The flag is read at module load, so stub the env then re-import the page.
    vi.stubEnv('VITE_ENABLE_GSI', 'false')
    vi.resetModules()
    const { default: FreshLoginPage } = await import('./LoginPage')

    render(<FreshLoginPage />)
    expect(screen.getByRole('button', { name: /^sign in$/i })).toBeInTheDocument()
    expect(screen.queryByLabelText('Sign in with Google')).not.toBeInTheDocument()
  })
})
