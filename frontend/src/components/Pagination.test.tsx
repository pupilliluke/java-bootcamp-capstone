import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi } from 'vitest'
import Pagination, { pageItems } from './Pagination'

// The window is what keeps the control inside the card. A book of 1000 at 8 a
// page is 125 pages, and every one of them used to get a button.
describe('pageItems', () => {
  it('lists every page when they all fit', () => {
    expect(pageItems(1, 5)).toEqual([1, 2, 3, 4, 5])
    expect(pageItems(3, 7)).toEqual([1, 2, 3, 4, 5, 6, 7])
  })

  it('never returns more than the window, however large the book', () => {
    for (const page of [1, 2, 63, 124, 125]) {
      expect(pageItems(page, 125).length).toBeLessThanOrEqual(7)
    }
  })

  it('always keeps the first and last page reachable', () => {
    const items = pageItems(63, 125)
    expect(items[0]).toBe(1)
    expect(items[items.length - 1]).toBe(125)
  })

  it('elides only where pages are actually skipped', () => {
    // Near the start there is nothing to elide on the left.
    expect(pageItems(1, 125)).toEqual([1, 2, 3, 4, null, 125])
    // Near the end, nothing to elide on the right.
    expect(pageItems(125, 125)).toEqual([1, null, 122, 123, 124, 125])
    // In the middle, both sides.
    expect(pageItems(63, 125)).toEqual([1, null, 62, 63, 64, null, 125])
  })

  it('keeps the current page in the window', () => {
    for (const page of [1, 2, 3, 40, 123, 124, 125]) {
      expect(pageItems(page, 125)).toContain(page)
    }
  })
})

describe('Pagination', () => {
  it('renders a bounded number of controls for a large book', () => {
    render(<Pagination page={63} pageSize={8} total={1000} onPage={vi.fn()} />)
    // Seven windowed pages at most, plus previous and next.
    expect(screen.getAllByRole('button').length).toBeLessThanOrEqual(9)
    expect(screen.getByText('Showing 497 to 504 of 1000 entries')).toBeInTheDocument()
  })

  it('marks the current page for assistive tech', () => {
    render(<Pagination page={3} pageSize={8} total={1000} onPage={vi.fn()} />)
    expect(screen.getByRole('button', { name: '3' })).toHaveAttribute('aria-current', 'page')
  })

  it('reports the page that was clicked', async () => {
    const onPage = vi.fn()
    render(<Pagination page={1} pageSize={8} total={1000} onPage={onPage} />)
    await userEvent.click(screen.getByRole('button', { name: '3' }))
    expect(onPage).toHaveBeenCalledWith(3)
  })

  it('disables previous on the first page and next on the last', () => {
    const { unmount } = render(<Pagination page={1} pageSize={8} total={1000} onPage={vi.fn()} />)
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled()
    unmount()

    render(<Pagination page={125} pageSize={8} total={1000} onPage={vi.fn()} />)
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled()
  })

  it('handles an empty list without claiming a row', () => {
    render(<Pagination page={1} pageSize={8} total={0} onPage={vi.fn()} />)
    expect(screen.getByText('Showing 0 to 0 of 0 entries')).toBeInTheDocument()
  })
})
