import type { ReactNode } from 'react'
import { useAuth } from './AuthContext'

// Shown when a non-admin reaches an admin screen. A whole page needs to say why
// it is empty; a single control inside a page does not, so callers guarding one
// button pass `fallback={null}` and it simply is not rendered.
const ACCESS_DENIED = (
    <section className="card">
        <h1>Access denied</h1>
        <p className="muted">
            Administrator access is required to manage users.
        </p>
    </section>
)

export default function AdminOnly({
    children,
    fallback = ACCESS_DENIED,
}: {
    children: ReactNode
    fallback?: ReactNode
}) {
    const { state } = useAuth()

    if (
        state.status !== 'authenticated' ||
        state.user.role !== 'ADMIN'
    ) {
        return <>{fallback}</>
    }

    return <>{children}</>
}
