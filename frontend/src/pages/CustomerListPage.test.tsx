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

  // --- multi-status checkbox filter --------------------------------------
  describe('status filter', () => {
    beforeEach(() => {
      mockUseCustomers.mockReturnValue({ customers: roster, loading: false, error: null })
    })

    it('shows every status by default, with "All" checked', () => {
      renderPage()
      expect(statusBox(/^all$/i)).toBeChecked()
      expect(rows()).toBe(4)
    })

    it('narrows to a single checked status', async () => {
      const user = userEvent.setup()
      renderPage()
      await user.click(statusBox(/^active$/i))

      expect(screen.getByText('Active Andy')).toBeInTheDocument()
      expect(screen.queryByText('Prospect Pia')).not.toBeInTheDocument()
      expect(rows()).toBe(1)
      // Selecting a specific status clears the "All" checkbox.
      expect(statusBox(/^all$/i)).not.toBeChecked()
    })

    it('shows the union of several checked statuses', async () => {
      const user = userEvent.setup()
      renderPage()
      await user.click(statusBox(/^active$/i))
      await user.click(statusBox(/^closed$/i))

      expect(screen.getByText('Active Andy')).toBeInTheDocument()
      expect(screen.getByText('Closed Chris')).toBeInTheDocument()
      expect(screen.queryByText('Prospect Pia')).not.toBeInTheDocument()
      expect(rows()).toBe(2)
    })

    it('falls back to All when the last status is unchecked', async () => {
      const user = userEvent.setup()
      renderPage()
      await user.click(statusBox(/^active$/i)) // narrow to 1
      expect(rows()).toBe(1)
      await user.click(statusBox(/^active$/i)) // uncheck it again

      expect(statusBox(/^all$/i)).toBeChecked()
      expect(rows()).toBe(4)
    })

    // Without the setPage(1) calls in showAll/toggle, this strands the user on
    // a page the filtered list no longer has, and the table renders empty.
    it('returns to page 1 when the filter changes', async () => {
      mockUseCustomers.mockReturnValue({ customers: bigRoster, loading: false, error: null })
      const user = userEvent.setup()
      renderPage()

      await user.click(screen.getByRole('button', { name: '2' }))
      expect(screen.getByText('Closed Chris')).toBeInTheDocument() // 11th row, page 2

      await user.click(statusBox(/^active$/i))

      expect(screen.getByRole('button', { name: '1' })).toHaveClass('active')
      expect(screen.getByText('Active Number 0')).toBeInTheDocument()
      expect(rows()).toBe(8)
    })

    // The pager and the table must agree about which page is showing, even when
    // the list shrinks underneath the current page without a filter change.
    it('keeps the table and the pager on the same page when the list shrinks', async () => {
      mockUseCustomers.mockReturnValue({ customers: bigRoster, loading: false, error: null })
      const user = userEvent.setup()
      const { rerender } = renderPage()

      await user.click(screen.getByRole('button', { name: '2' }))
      expect(rows()).toBe(3)

      // A refetch returns a shorter list — one page's worth — while the user is
      // still on page 2.
      mockUseCustomers.mockReturnValue({
        customers: bigRoster.slice(0, 3),
        loading: false,
        error: null,
      })
      rerender(<CustomerListPage navigate={navigate} reloadKey={1} />)

      expect(screen.queryByText(/no customers match/i)).not.toBeInTheDocument()
      expect(rows()).toBe(3)
    })
  })
})
