import type { KeyboardEvent } from 'react'
import type { Interaction } from '../types/customer'

// A vertical timeline for a customer's interactions, replacing the flat table.
// Plain CSS and React only, matching the rest of the design system: no UI
// library, no utility-class framework. Styles live in index.css under
// ".timeline".
//
// Grouped by day rather than listed flat, because "what happened on the 27th"
// is the question an agent actually asks, and a 40-row table answers it badly.

interface Props {
  interactions: Interaction[]
  // Customer id -> display name. Supplied by the dashboard, where one feed
  // mixes several customers and "who" is the first thing you need. Omitted on
  // a customer's own page, where every row is obviously theirs.
  customerNames?: Record<string, string>
  // Called when a row is activated. Only wired up on the dashboard, where the
  // feed is a way into the customer.
  onSelect?: (customerId: string) => void
}

// Channel drives the accent colour of the node and badge. Keeping the map here
// rather than in CSS means an unknown channel from the API degrades to the
// neutral style instead of rendering an unstyled dot.
const CHANNEL_CLASS: Record<string, string> = {
  PHONE: 'tl-phone',
  EMAIL: 'tl-email',
  CHAT: 'tl-chat',
}

function dayKey(iso: string): string {
  return new Date(iso).toDateString()
}

// "Today" and "Yesterday" beat a date string for the two groups an agent reads
// most; anything older gets the full date.
function dayLabel(iso: string): string {
  const d = new Date(iso)
  const today = new Date()
  const yesterday = new Date()
  yesterday.setDate(today.getDate() - 1)

  if (d.toDateString() === today.toDateString()) return 'Today'
  if (d.toDateString() === yesterday.toDateString()) return 'Yesterday'
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric', year: 'numeric' })
}

function timeLabel(iso: string): string {
  return new Date(iso).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })
}

export default function InteractionTimeline({ interactions, customerNames, onSelect }: Props) {
  // Newest first. The API already orders by occurredAt, but sorting here keeps
  // the component correct if it is ever handed an unordered list.
  const ordered = [...interactions].sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
  )

  const groups: { key: string; label: string; items: Interaction[] }[] = []
  for (const it of ordered) {
    const key = dayKey(it.createdAt)
    const last = groups[groups.length - 1]
    if (last && last.key === key) last.items.push(it)
    else groups.push({ key, label: dayLabel(it.createdAt), items: [it] })
  }

  return (
    <ol className="timeline" aria-label="Interaction history">
      {groups.map((group) => (
        <li key={group.key} className="tl-group">
          <p className="tl-day">{group.label}</p>
          <ol className="tl-items">
            {group.items.map((it) => (
              <li key={it.interactionId} className={`tl-item ${CHANNEL_CLASS[it.channel] ?? ''}`}>
                <span className="tl-node" aria-hidden="true" />
                <div
                  className={`tl-body${onSelect ? ' tl-clickable' : ''}`}
                  // A button would be the obvious control, but the row carries
                  // its own text and time; making the whole card a button
                  // flattens that into one label. role + key handler keeps it
                  // reachable without nesting interactive content.
                  {...(onSelect
                    ? {
                        role: 'button',
                        tabIndex: 0,
                        onClick: () => onSelect(it.customerId),
                        onKeyDown: (e: KeyboardEvent) => {
                          if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault()
                            onSelect(it.customerId)
                          }
                        },
                      }
                    : {})}
                >
                  <div className="tl-head">
                    <span className="tl-channel">{it.channel}</span>
                    {customerNames?.[it.customerId] && (
                      <span className="tl-customer">{customerNames[it.customerId]}</span>
                    )}
                    <time className="tl-time" dateTime={it.createdAt}>
                      {timeLabel(it.createdAt)}
                    </time>
                  </div>
                  <p className="tl-notes">{it.notes}</p>
                  {/* The interaction id is the thread that ties this row to the
                      API response, the Kafka event and the consumer log. */}
                  <p className="tl-id" title={it.interactionId}>{it.interactionId}</p>
                </div>
              </li>
            ))}
          </ol>
        </li>
      ))}
    </ol>
  )
}
