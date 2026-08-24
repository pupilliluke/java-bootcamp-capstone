import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import CustomerListPage from './CustomerListPage'
import { useCustomers } from '../hooks/useCustomers'

// Mock the data hook so each state (loading / empty / error / populated) is
// deterministic without a network call.
vi.mock('../hooks/useCustomers')
const mockUseCustomers = vi.mocked(useCustomers)

const navigate = vi.fn()
const renderPage = () => render(<CustomerListPage navigate={navigate} reloadKey={0} />)

const customer = {
  customerId: 'CUS-1001',
  fullName: 'Amina Khan',
  email: 'amina.khan@example.test',
  phone: '555-0101',
  status: 'ACTIVE' as const,
}

beforeEach(() => vi.clearAllMocks())

describe('CustomerListPage', () => {
  it('shows a loading state', () => {
    mockUseCustomers.mockReturnValue({ customers: [], loading: true, error: null })
    renderPage()
    expect(screen.getByText(/loading customers/i)).toBeInTheDocument()
  })

  it('shows an error state', () => {
    mockUseCustomers.mockReturnValue({ customers: [], loading: false, error: 'Network error' })
    renderPage()
    expect(screen.getByRole('alert')).toHaveTextContent(/network error/i)
  })

  it('shows an empty state when there are no customers', () => {
    mockUseCustomers.mockReturnValue({ customers: [], loading: false, error: null })
    renderPage()
    expect(screen.getByText(/no customers match your search/i)).toBeInTheDocument()
  })

  it('renders customer rows when populated', () => {
    mockUseCustomers.mockReturnValue({ customers: [customer], loading: false, error: null })
    renderPage()
    expect(screen.getByText('Amina Khan')).toBeInTheDocument()
    expect(screen.getByText('CUS-1001')).toBeInTheDocument()
  })
})
