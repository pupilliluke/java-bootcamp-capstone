import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import CustomerDetailsPage from './CustomerDetailsPage'
import { useCustomer } from '../hooks/useCustomer'

vi.mock('../hooks/useCustomer')
const mockUseCustomer = vi.mocked(useCustomer)

const navigate = vi.fn()
const renderPage = () =>
  render(<CustomerDetailsPage customerId="CUS-1001" navigate={navigate} />)

const customer = {
  customerId: 'CUS-1001',
  fullName: 'Amina Khan',
  email: 'amina.khan@example.test',
  phone: '555-0101',
  status: 'ACTIVE' as const,
}

beforeEach(() => vi.clearAllMocks())

describe('CustomerDetailsPage', () => {
  it('shows a loading state', () => {
    mockUseCustomer.mockReturnValue({ customer: null, loading: true, error: null })
    renderPage()
    expect(screen.getByText(/loading customer/i)).toBeInTheDocument()
  })

  it('shows an error / not-found state', () => {
    mockUseCustomer.mockReturnValue({ customer: null, loading: false, error: null })
    renderPage()
    expect(screen.getByText(/customer not found/i)).toBeInTheDocument()
  })

  it('surfaces a fetch error message', () => {
    mockUseCustomer.mockReturnValue({ customer: null, loading: false, error: 'Network error' })
    renderPage()
    expect(screen.getByText(/network error/i)).toBeInTheDocument()
  })

  it('renders the profile when populated', () => {
    mockUseCustomer.mockReturnValue({ customer, loading: false, error: null })
    renderPage()
    expect(screen.getByText('Customer Details')).toBeInTheDocument()
    // Name shows in both the profile header and the Overview row.
    expect(screen.getAllByText('Amina Khan').length).toBeGreaterThan(0)
  })
})
