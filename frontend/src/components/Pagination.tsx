interface Props {
  page: number // 1-based
  pageSize: number
  total: number
  onPage: (page: number) => void
}

export default function Pagination({ page, pageSize, total, onPage }: Props) {
  const pageCount = Math.max(1, Math.ceil(total / pageSize))
  const from = total === 0 ? 0 : (page - 1) * pageSize + 1
  const to = Math.min(page * pageSize, total)
  const pages = Array.from({ length: pageCount }, (_, i) => i + 1)

  return (
    <div className="pagination">
      <span className="muted">
        Showing {from} to {to} of {total} entries
      </span>
      <div className="pages">
        <button className="page-btn" disabled={page <= 1} onClick={() => onPage(page - 1)}>
          ‹
        </button>
        {pages.map((p) => (
          <button
            key={p}
            className={`page-btn${p === page ? ' active' : ''}`}
            onClick={() => onPage(p)}
          >
            {p}
          </button>
        ))}
        <button className="page-btn" disabled={page >= pageCount} onClick={() => onPage(page + 1)}>
          ›
        </button>
      </div>
    </div>
  )
}
