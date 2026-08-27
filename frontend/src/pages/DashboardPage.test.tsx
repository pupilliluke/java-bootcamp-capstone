import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import DashboardPage from './DashboardPage'
import { useCustomers } from '../hooks/useCustomers'

vi.mock('../hooks/useCustomers')
const mockUseCustomers = vi.mocked(useCustomers)

const navigate = vi.fn()
const renderPage = () => render(<DashboardPage navigate={navigate} reloadKey={0} />)

const customer = {
  customerId: 'CUS-1001',
  fullName: 'Amina Khan',
  email: 'amina.khan@example.test',
  phone: '555-0101',
  status: 'ACTIVE' as const,
}

beforeEach(() => vi.clearAllMocks())

describe('DashboardPage', () => {
  it('shows a loading state', () => {
    mockUseCustomers.mockReturnValue({ customers: [], totalElements: 0, totalPages: 1, loading: true, error: null })
    renderPage()
    expect(screen.getByText(/loading/i)).toBeInTheDocument()
  })

  it('shows an error state', () => {
    mockUseCustomers.mockReturnValue({ customers: [], totalElements: 0, totalPages: 1, loading: false, error: 'Network error' })
    renderPage()
    expect(screen.getByText(/is the backend running/i)).toBeInTheDocument()
  })

  it('shows an empty state when there are no customers', () => {
    mockUseCustomers.mockReturnValue({ customers: [], totalElements: 0, totalPages: 1, loading: false, error: null })
    renderPage()
    expect(screen.getByText(/no customers yet/i)).toBeInTheDocument()
  })

  it('renders KPIs and recent customers when populated', () => {
    mockUseCustomers.mockReturnValue({ customers: [customer], totalElements: [customer].length, totalPages: 1, loading: false, error: null })
    renderPage()
    expect(screen.getByText('Total Customers')).toBeInTheDocument()
    expect(screen.getByText('Amina Khan')).toBeInTheDocument()
  })
})
