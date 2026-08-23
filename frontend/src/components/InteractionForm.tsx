import { useState } from 'react'
import type { Channel, CreateInteraction } from '../types/customer'

interface Props {
  onSubmit: (body: CreateInteraction) => void
  saving: boolean
}

export default function InteractionForm({ onSubmit, saving }: Props) {
  const [channel, setChannel] = useState<Channel>('PHONE')
  const [summary, setSummary] = useState('')

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!summary.trim()) return
    onSubmit({ channel, summary: summary.trim() })
    setSummary('')
  }

  return (
    <form onSubmit={handleSubmit}>
      <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginBottom: '0.5rem' }}>
        <label htmlFor="channel">Channel</label>
        <select id="channel" value={channel} onChange={(e) => setChannel(e.target.value as Channel)}>
          <option>PHONE</option>
          <option>EMAIL</option>
          <option>CHAT</option>
        </select>
      </div>
      <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
        <label htmlFor="summary">Summary</label>
        <input
          id="summary"
          value={summary}
          onChange={(e) => setSummary(e.target.value)}
          placeholder="Called about renewal"
        />
        <button type="submit" disabled={saving}>
          {saving ? 'Saving…' : 'Add'}
        </button>
      </div>
    </form>
  )
}
