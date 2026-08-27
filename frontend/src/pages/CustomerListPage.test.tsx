import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
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

// A spread of statuses for the filter tests.
const roster = [
  { ...customer, customerId: 'CUS-1001', fullName: 'Active Andy', status: 'ACTIVE' as const },
  { ...customer, customerId: 'CUS-1002', fullName: 'Prospect Pia', status: 'PROSPECT' as const },
  { ...customer, customerId: 'CUS-1003', fullName: 'Suspended Sam', status: 'SUSPENDED' as const },
  { ...customer, customerId: 'CUS-1004', fullName: 'Closed Chris', status: 'CLOSED' as const },
]

// The status checkbox, found by its accessible label ("Active", "Closed", ...).
const statusBox = (name: RegExp) =>
  within(screen.getByRole('group', { name: /filter by status/i })).getByRole('checkbox', { name })

const rows = () => screen.getAllByRole('row').length - 1 // minus the header row

// PAGE_SIZE is 8, so a roster of four can never paginate. Pagination behaviour
// needs a list long enough to have a second page: 10 ACTIVE plus one CLOSED, so
// filtering to CLOSED from page 2 collapses the result to a single page.
const bigRoster = [
  ...Array.from({ length: 10 }, (_, i) => ({
    ...customer,
    customerId: `CUS-2${String(i).padStart(3, '0')}`,
    fullName: `Active Number ${i}`,
    status: 'ACTIVE' as const,
  })),
  { ...customer, customerId: 'CUS-3001', fullName: 'Closed Chris', status: 'CLOSED' as const },
]

beforeEach(() => vi.clearAllMocks())

describe('CustomerListPage', () => {
  it('shows a loading state', () => {
    mockUseCustomers.mockReturnValue({ customers: [], totalElements: 0, totalPages: 1, loading: true, error: null })
    renderPage()
    expect(screen.getByText(/loading customers/i)).toBeInTheDocument()
  })

  it('shows an error state', () => {
    mockUseCustomers.mockReturnValue({ customers: [], totalElements: 0, totalPages: 1, loading: false, error: 'Network error' })
    renderPage()
    expect(screen.getByRole('alert')).toHaveTextContent(/network error/i)
  })

  it('shows an empty state when there are no customers', () => {
    mockUseCustomers.mockReturnValue({ customers: [], totalElements: 0, totalPages: 1, loading: false, error: null })
    renderPage()
    expect(screen.getByText(/no customers match your search/i)).toBeInTheDocument()
  })

  it('renders customer rows when populated', () => {
    mockUseCustomers.mockReturnValue({ customers: [customer], totalElements: [customer].length, totalPages: 1, loading: false, error: null })
    renderPage()
    expect(screen.getByText('Amina Khan')).toBeInTheDocument()
    expect(screen.getByText('CUS-1001')).toBeInTheDocument()
  })

  // --- multi-status checkbox filter --------------------------------------
  describe('status filter', () => {
    beforeEach(() => {
      mockUseCustomers.mockReturnValue({ customers: roster, totalElements: roster.length, totalPages: 1, loading: false, error: null })
    })

    it('shows every status by default, with "All" checked', () => {
      renderPage()
      expect(statusBox(/^all$/i)).toBeChecked()
      expect(rows()).toBe(4)
    })

    // Filtering moved to the server, so these assert the request the page makes
    // rather than the rows it keeps. Asserting on rows here would pass whatever
    // the mock returned and prove nothing about the filter.
    const lastCall = () => mockUseCustomers.mock.calls[mockUseCustomers.mock.calls.length - 1][0]

    it('asks the server for a single checked status', async () => {
      const user = userEvent.setup()
      renderPage()
      await user.click(statusBox(/^active$/i))

      expect(lastCall()?.statuses).toEqual(['ACTIVE'])
      // Selecting a specific status clears the "All" checkbox.
      expect(statusBox(/^all$/i)).not.toBeChecked()
    })

    it('asks for the union of several checked statuses', async () => {
      const user = userEvent.setup()
      renderPage()
      await user.click(statusBox(/^active$/i))
      await user.click(statusBox(/^closed$/i))

      expect(lastCall()?.statuses).toEqual(expect.arrayContaining(['ACTIVE', 'CLOSED']))
      expect(lastCall()?.statuses).toHaveLength(2)
    })

    it('falls back to every status when the last one is unchecked', async () => {
      const user = userEvent.setup()
      renderPage()
      await user.click(statusBox(/^active$/i))
      await user.click(statusBox(/^active$/i))

      expect(statusBox(/^all$/i)).toBeChecked()
      expect(lastCall()?.statuses).toHaveLength(4)
    })

    // Without the setPage(1) calls in showAll/toggle, the page stays on nine
    // while the filtered result has one, and the server answers with nothing.
    it('returns to the first page when the filter changes', async () => {
      mockUseCustomers.mockReturnValue({
        customers: bigRoster.slice(0, 8),
        totalElements: 96,
        totalPages: 12,
        loading: false,
        error: null,
      })
      const user = userEvent.setup()
      renderPage()

      await user.click(screen.getByRole('button', { name: '2' }))
      expect(lastCall()?.page).toBe(1) // zero-based: screen page 2

      await user.click(statusBox(/^active$/i))
      expect(lastCall()?.page).toBe(0)
      expect(screen.getByRole('button', { name: '1' })).toHaveClass('active')
    })

    // The pager and the table must agree, even when the book shrinks underneath
    // the current page.
    it('snaps back when the current page no longer exists', async () => {
      mockUseCustomers.mockReturnValue({
        customers: bigRoster.slice(0, 8),
        totalElements: 96,
        totalPages: 12,
        loading: false,
        error: null,
      })
      const user = userEvent.setup()
      const { rerender } = renderPage()
      await user.click(screen.getByRole('button', { name: '3' }))

      mockUseCustomers.mockReturnValue({
        customers: bigRoster.slice(0, 3),
        totalElements: 3,
        totalPages: 1,
        loading: false,
        error: null,
      })
      rerender(<CustomerListPage navigate={navigate} reloadKey={1} />)

      expect(screen.queryByText(/no customers match/i)).not.toBeInTheDocument()
      expect(rows()).toBe(3)
    })
  })
})
