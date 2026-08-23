import type { Interaction } from '../types/customer'

export default function InteractionTimeline({ interactions }: { interactions: Interaction[] }) {
  if (interactions.length === 0) {
    return <p style={{ color: '#666' }}>No interactions yet.</p>
  }
  return (
    <ul>
      {interactions.map((it, i) => (
        <li key={i}>
          <strong>{it.channel}</strong> — {it.summary}{' '}
          <span style={{ color: '#666' }}>({new Date(it.createdAt).toLocaleString()})</span>
        </li>
      ))}
    </ul>
  )
}
