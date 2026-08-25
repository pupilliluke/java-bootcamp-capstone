import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi } from 'vitest'
import LoginPage from './LoginPage'

// useAuth is the only dependency; stub it so no real auth/network runs.
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ login: vi.fn(), logout: vi.fn(), state: { status: 'anonymous' } }),
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
