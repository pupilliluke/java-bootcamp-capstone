import { useState } from 'react'

interface Props {
  onSearch: (customerId: string) => void
  loading: boolean
}

export default function SearchBar({ onSearch, loading }: Props) {
  const [query, setQuery] = useState('CUS-1001')

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (query.trim()) onSearch(query)
  }

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
      <label htmlFor="q">Customer ID</label>
      <input id="q" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="CUS-1001" />
      <button type="submit" disabled={loading}>
        {loading ? 'Searching…' : 'Search'}
      </button>
    </form>
  )
}
