import { useState } from 'react'
import { useCustomerSearch } from './hooks/useCustomerSearch'
import { interactionsApi } from './api/interactions'
import { ApiError } from './api/ApiError'
import type { CreateInteraction, Interaction } from './types/customer'
import SearchBar from './components/SearchBar'
import CustomerProfile from './components/CustomerProfile'
import InteractionForm from './components/InteractionForm'
import InteractionTimeline from './components/InteractionTimeline'

// Composition root. Mirrors the Lab 35 structure: thin component that wires the
// hook + api modules together. Journey: search -> profile -> record interaction.
export default function App() {
  const { customer, loading, error, search } = useCustomerSearch()

  const [interactions, setInteractions] = useState<Interaction[]>([])
  const [saving, setSaving] = useState(false)
  const [formNote, setFormNote] = useState('')

  async function handleAddInteraction(body: CreateInteraction) {
    if (!customer) return
    setSaving(true)
    setFormNote('')
    const entry: Interaction = { ...body, createdAt: new Date().toISOString() }
    try {
      await interactionsApi.create(customer.customerId, body)
      setFormNote('Saved to backend ✓')
    } catch (e) {
      // Backend endpoint not built yet — keep the demo walkable, but be honest.
      const detail =
        e instanceof ApiError ? ` (${e.kind}${e.status ? ' ' + e.status : ''})` : ''
      setFormNote(`Shown locally only — backend interaction endpoint not built yet${detail}`)
    } finally {
      setInteractions((prev) => [entry, ...prev])
      setSaving(false)
    }
  }

  // reset the local timeline whenever a new customer is searched
  function handleSearch(id: string) {
    setInteractions([])
    setFormNote('')
    search(id)
  }

  return (
    <main style={styles.page}>
      <h1>Northstar CRM — skeleton</h1>
      <p style={styles.muted}>
        Demo journey: search → profile → record interaction. Fixtures: CUS-1001
        (Amina Khan), CUS-1002 (Ravi Singh).
      </p>

      <section style={styles.card}>
        <h2>1. Search customer</h2>
        <SearchBar onSearch={handleSearch} loading={loading} />
        {error && (
          <p style={styles.error} role="alert">
            {error}
          </p>
        )}
      </section>

      {customer && (
        <section style={styles.card}>
          <h2>2. Profile</h2>
          <CustomerProfile customer={customer} />
        </section>
      )}

      {customer && (
        <section style={styles.card}>
          <h2>3. Record interaction</h2>
          <InteractionForm onSubmit={handleAddInteraction} saving={saving} />
          {formNote && <p style={styles.muted}>{formNote}</p>}
          <h3>Timeline</h3>
          <InteractionTimeline interactions={interactions} />
        </section>
      )}
    </main>
  )
}

const styles: Record<string, React.CSSProperties> = {
  page: { fontFamily: 'system-ui, sans-serif', maxWidth: 640, margin: '2rem auto', padding: '0 1rem' },
  card: { border: '1px solid #ccc', borderRadius: 8, padding: '1rem', marginTop: '1rem' },
  muted: { color: '#666', fontSize: '0.9rem' },
  error: { color: '#b00020' },
}
