import { useEffect, useState } from 'react'
import { adminApi, type PendingUser } from '../api/admin'
import type { Navigate } from '../nav'

type State =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'ready'; pending: PendingUser[] }

// A dashboard notice that accounts are waiting for an admin's approval. It is a
// VIEW OF STATE, not a message: it shows only while listPending() returns a
// non-empty list and vanishes on its own once the list is empty, so there is no
// dismiss button -- approve the last pending user in Settings and it is simply
// gone when the dashboard next renders.
//
// Mount it behind <AdminOnly fallback={null}>. That is what stops an AGENT from
// ever making the hasRole('ADMIN') request: the component -- and the fetch in
// its effect -- never mounts for them. A 403 in the console is not a feature.
export default function PendingApprovalsNotice({
  navigate,
  reloadKey,
}: {
  navigate: Navigate
  reloadKey: number
}) {
  const [state, setState] = useState<State>({ status: 'loading' })

  // Re-checked on reloadKey so returning to the dashboard after approving
  // someone reflects the new, possibly-empty list rather than a stale count.
  useEffect(() => {
    const controller = new AbortController()
    setState({ status: 'loading' })
    adminApi
      .listPending(controller.signal)
      .then((pending) => setState({ status: 'ready', pending }))
      .catch(() => {
        if (!controller.signal.aborted) setState({ status: 'error' })
      })
    return () => controller.abort()
  }, [reloadKey])

  // Loading and empty both render nothing: an additive notice should not flash a
  // skeleton for something usually absent. The rule that matters is never
  // rendering a count we do not have -- a failed poll must not read "0 waiting",
  // which is why the error branch says it could not check rather than showing a
  // number.
  if (state.status === 'loading') return null

  if (state.status === 'error') {
    return (
      <div className="pending-notice pending-notice--error" role="status" aria-live="polite">
        Couldn’t check for accounts awaiting approval.
      </div>
    )
  }

  const count = state.pending.length
  if (count === 0) return null

  const label =
    count === 1
      ? '1 account is waiting for approval'
      : `${count} accounts are waiting for approval`

  // role="status" + aria-live so the count is announced when the notice appears,
  // not only seen -- an accessibility affordance on a value that genuinely
  // changes (Lab 33). The button is the single navigation into the existing
  // Settings pending list; there is no second approve path here.
  return (
    <div role="status" aria-live="polite">
      <button
        type="button"
        className="pending-notice"
        onClick={() => navigate({ name: 'settings' })}
      >
        <span className="pending-notice__count">{label}</span>
        <span className="pending-notice__cta">Review in Settings →</span>
      </button>
    </div>
  )
}
