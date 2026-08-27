interface Props {
  page: number // 1-based
  pageSize: number
  total: number
  onPage: (page: number) => void
}

// How many numbered buttons to show at most. Seven fits the card at the
// narrowest width the list is used at, and keeps first, last, the current page
// and one either side visible at the same time.
const WINDOW = 7

// The numbers to render, with nulls where a run is elided.
//
// Every page used to get a button, which is fine for the seeded demo book and
// breaks at any real size: 1000 customers at 8 a page is 125 buttons, and the
// row ran straight out of the card. Only ever WINDOW entries are returned now,
// so the control is the same width whether the book holds ten customers or ten
// thousand.
export function pageItems(page: number, pageCount: number): (number | null)[] {
  if (pageCount <= WINDOW) {
    return Array.from({ length: pageCount }, (_, i) => i + 1)
  }

  // First and last are always reachable; the window slides between them.
  const items: (number | null)[] = [1]

  // Clamped so the window keeps its size at both ends rather than shrinking:
  // near page 1 it runs 2,3,4,5; near the end it runs the last four before it.
  let start = Math.max(2, page - 1)
  let end = Math.min(pageCount - 1, page + 1)
  if (page <= 3) end = Math.min(pageCount - 1, WINDOW - 3)
  if (page >= pageCount - 2) start = Math.max(2, pageCount - (WINDOW - 3) + 1)

  // null is an elided run, not a page. It renders as an ellipsis and is not a
  // button, so nothing focusable appears where there is nothing to click.
  if (start > 2) items.push(null)
  for (let p = start; p <= end; p++) items.push(p)
  if (end < pageCount - 1) items.push(null)

  items.push(pageCount)
  return items
}

export default function Pagination({ page, pageSize, total, onPage }: Props) {
  const pageCount = Math.max(1, Math.ceil(total / pageSize))
  const from = total === 0 ? 0 : (page - 1) * pageSize + 1
  const to = Math.min(page * pageSize, total)
  const items = pageItems(page, pageCount)

  return (
    <div className="pagination">
      <span className="muted">
        Showing {from} to {to} of {total} entries
      </span>
      <nav className="pages" aria-label="Pagination">
        <button
          className="page-btn"
          disabled={page <= 1}
          onClick={() => onPage(page - 1)}
          aria-label="Previous page"
        >
          ‹
        </button>
        {items.map((p, i) =>
          p === null ? (
            <span key={`gap-${i}`} className="page-gap" aria-hidden="true">
              …
            </span>
          ) : (
            <button
              key={p}
              className={`page-btn${p === page ? ' active' : ''}`}
              onClick={() => onPage(p)}
              // No aria-label: the visible number is the accessible name, and
              // the surrounding nav already says what these numbers are.
              aria-current={p === page ? 'page' : undefined}
            >
              {p}
            </button>
          ),
        )}
        <button
          className="page-btn"
          disabled={page >= pageCount}
          onClick={() => onPage(page + 1)}
          aria-label="Next page"
        >
          ›
        </button>
      </nav>
    </div>
  )
}
