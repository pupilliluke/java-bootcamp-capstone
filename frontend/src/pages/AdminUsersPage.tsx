import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { adminUsersApi } from '../api/v1/adminUsers'
import { ApiError } from '../api/v1/ApiError'
import { useAuth } from '../auth/AuthContext'
import type { AdminUser, UserRole } from '../types/user'

interface UserForm {
    username: string
    email: string
    password: string
    role: UserRole
    enabled: boolean
}

const EMPTY_FORM: UserForm = {
    username: '',
    email: '',
    password: '',
    role: 'AGENT',
    enabled: true,
}

function errorMessage(error: unknown): string {
    return error instanceof Error
        ? error.message
        : 'An unexpected error occurred'
}

export default function AdminUsersPage() {
    const { state } = useAuth()
    const [users, setUsers] = useState<AdminUser[]>([])
    const [form, setForm] = useState<UserForm>(EMPTY_FORM)
    const [editingId, setEditingId] = useState<number | null>(null)
    const [loading, setLoading] = useState(true)
    const [submitting, setSubmitting] = useState(false)
    const [error, setError] = useState<string | null>(null)
    const [success, setSuccess] = useState<string | null>(null)

    const currentUsername =
        state.status === 'authenticated' ? state.user.username : ''

    useEffect(() => {
        const controller = new AbortController()

        adminUsersApi
            .list(controller.signal)
            .then(setUsers)
            .catch((requestError: unknown) => {
                if (
                    requestError instanceof ApiError &&
                    requestError.kind === 'abort'
                ) {
                    return
                }

                setError(errorMessage(requestError))
            })
            .finally(() => {
                if (!controller.signal.aborted) {
                    setLoading(false)
                }
            })

        return () => controller.abort()
    }, [])

    function resetForm() {
        setEditingId(null)
        setForm(EMPTY_FORM)
        setError(null)
    }

    function beginEdit(user: AdminUser) {
        setEditingId(user.id)
        setForm({
            username: user.username,
            email: user.email,
            password: '',
            role: user.role,
            enabled: user.enabled,
        })
        setError(null)
        setSuccess(null)
    }

    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault()
        setSubmitting(true)
        setError(null)
        setSuccess(null)

        try {
            if (editingId === null) {
                const created = await adminUsersApi.create({
                    username: form.username.trim(),
                    email: form.email.trim(),
                    password: form.password,
                    role: form.role,
                })

                setUsers((current) => [...current, created])
                setSuccess(`Created user ${created.username}`)
            } else {
                const updated = await adminUsersApi.update(editingId, {
                    email: form.email.trim(),
                    newPassword: form.password,
                    role: form.role,
                    enabled: form.enabled,
                })

                setUsers((current) =>
                    current.map((user) =>
                        user.id === updated.id ? updated : user,
                    ),
                )
                setSuccess(`Updated user ${updated.username}`)
            }

            setEditingId(null)
            setForm(EMPTY_FORM)
        } catch (requestError) {
            setError(errorMessage(requestError))
        } finally {
            setSubmitting(false)
        }
    }

    async function removeUser(user: AdminUser) {
        const confirmed = window.confirm(
            `Delete user "${user.username}"? This cannot be undone.`,
        )

        if (!confirmed) return

        setError(null)
        setSuccess(null)

        try {
            await adminUsersApi.remove(user.id)
            setUsers((current) =>
                current.filter((existing) => existing.id !== user.id),
            )

            if (editingId === user.id) {
                setEditingId(null)
                setForm(EMPTY_FORM)
            }

            setSuccess(`Deleted user ${user.username}`)
        } catch (requestError) {
            setError(errorMessage(requestError))
        }
    }

    return (
        <section>
            <div className="page-header">
                <div>
                    <h1>User Management</h1>
                    <p className="muted">
                        Create, edit, disable, and delete CRM users.
                    </p>
                </div>

                <button
                    className="btn-primary"
                    type="button"
                    onClick={resetForm}
                >
                    New User
                </button>
            </div>

            {error && (
                <p className="error" role="alert">
                    {error}
                </p>
            )}

            {success && (
                <p className="success" role="status">
                    {success}
                </p>
            )}

            <div className="card">
                <h2>Users</h2>

                {loading ? (
                    <div className="spinner-row">Loading users…</div>
                ) : users.length === 0 ? (
                    <div className="empty">No users found.</div>
                ) : (
                    <div className="table-wrap">
                        <table className="data">
                            <thead>
                            <tr>
                                <th>Username</th>
                                <th>Email</th>
                                <th>Role</th>
                                <th>Status</th>
                                <th>Created</th>
                                <th>Actions</th>
                            </tr>
                            </thead>

                            <tbody>
                            {users.map((user) => {
                                const isCurrentUser =
                                    user.username.toLowerCase() ===
                                    currentUsername.toLowerCase()

                                return (
                                    <tr key={user.id}>
                                        <td>
                                            <strong>{user.username}</strong>
                                            {isCurrentUser && (
                                                <span className="muted"> (you)</span>
                                            )}
                                        </td>
                                        <td>{user.email}</td>
                                        <td>
                        <span className="badge badge-channel">
                          {user.role}
                        </span>
                                        </td>
                                        <td>
                        <span
                            className={`badge badge-${
                                user.enabled ? 'ACTIVE' : 'INACTIVE'
                            }`}
                        >
                          {user.enabled ? 'Enabled' : 'Disabled'}
                        </span>
                                        </td>
                                        <td>
                                            {new Date(user.createdAt).toLocaleDateString()}
                                        </td>
                                        <td>
                                            <div className="actions-row">
                                                <button
                                                    className="btn-ghost"
                                                    type="button"
                                                    disabled={isCurrentUser}
                                                    title={
                                                        isCurrentUser
                                                            ? 'You cannot edit your own account'
                                                            : 'Edit user'
                                                    }
                                                    onClick={() => beginEdit(user)}
                                                >
                                                    Edit
                                                </button>

                                                <button
                                                    className="btn-danger"
                                                    type="button"
                                                    disabled={isCurrentUser}
                                                    title={
                                                        isCurrentUser
                                                            ? 'You cannot delete your own account'
                                                            : 'Delete user'
                                                    }
                                                    onClick={() => removeUser(user)}
                                                >
                                                    Delete
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                )
                            })}
                            </tbody>
                        </table>
                    </div>
                )}
            </div>

            <div className="card">
                <h2>
                    {editingId === null ? 'Create User' : `Edit ${form.username}`}
                </h2>

                <form onSubmit={submit}>
                    <div className="form-grid">
                        <div className="form-field">
                            <label htmlFor="admin-username">
                                Username <span className="req">*</span>
                            </label>
                            <input
                                id="admin-username"
                                value={form.username}
                                disabled={editingId !== null}
                                required
                                minLength={3}
                                maxLength={100}
                                pattern="[A-Za-z0-9._-]+"
                                onChange={(event) =>
                                    setForm({
                                        ...form,
                                        username: event.target.value,
                                    })
                                }
                            />
                        </div>

                        <div className="form-field">
                            <label htmlFor="admin-email">
                                Email <span className="req">*</span>
                            </label>
                            <input
                                id="admin-email"
                                type="email"
                                value={form.email}
                                required
                                maxLength={255}
                                onChange={(event) =>
                                    setForm({
                                        ...form,
                                        email: event.target.value,
                                    })
                                }
                            />
                        </div>

                        <div className="form-field">
                            <label htmlFor="admin-password">
                                {editingId === null
                                    ? 'Password'
                                    : 'New password (leave blank to keep current)'}
                                {editingId === null && <span className="req"> *</span>}
                            </label>
                            <input
                                id="admin-password"
                                type="password"
                                value={form.password}
                                required={editingId === null}
                                minLength={form.password ? 8 : undefined}
                                maxLength={72}
                                autoComplete="new-password"
                                onChange={(event) =>
                                    setForm({
                                        ...form,
                                        password: event.target.value,
                                    })
                                }
                            />
                        </div>

                        <div className="form-field">
                            <label htmlFor="admin-role">
                                Role <span className="req">*</span>
                            </label>
                            <select
                                id="admin-role"
                                value={form.role}
                                onChange={(event) =>
                                    setForm({
                                        ...form,
                                        role: event.target.value as UserRole,
                                    })
                                }
                            >
                                <option value="AGENT">Agent</option>
                                <option value="ADMIN">Admin</option>
                            </select>
                        </div>

                        {editingId !== null && (
                            <label className="checkbox-row">
                                <input
                                    type="checkbox"
                                    checked={form.enabled}
                                    onChange={(event) =>
                                        setForm({
                                            ...form,
                                            enabled: event.target.checked,
                                        })
                                    }
                                />
                                Account enabled
                            </label>
                        )}
                    </div>

                    <div className="admin-form-actions">
                        <button
                            className="btn-primary"
                            type="submit"
                            disabled={submitting}
                        >
                            {submitting
                                ? 'Saving…'
                                : editingId === null
                                    ? 'Create User'
                                    : 'Save Changes'}
                        </button>

                        {editingId !== null && (
                            <button
                                className="btn-secondary"
                                type="button"
                                disabled={submitting}
                                onClick={resetForm}
                            >
                                Cancel
                            </button>
                        )}
                    </div>
                </form>
            </div>
        </section>
    )
}