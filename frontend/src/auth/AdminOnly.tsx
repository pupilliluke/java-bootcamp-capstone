import type { ReactNode } from 'react'
import { useAuth } from './AuthContext'

export default function AdminOnly({ children }: { children: ReactNode }) {
    const { state } = useAuth()

    if (
        state.status !== 'authenticated' ||
        state.user.role !== 'ADMIN'
    ) {
        return (
            <section className="card">
                <h1>Access denied</h1>
                <p className="muted">
                    Administrator access is required to manage users.
                </p>
            </section>
        )
    }

    return <>{children}</>
}