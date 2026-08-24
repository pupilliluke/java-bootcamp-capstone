import type {
    AdminUser,
    CreateUserInput,
    UpdateUserInput,
} from '../types/user'
import { http } from './http'

const basePath = '/api/admin/users'

export const adminUsersApi = {
    list(signal?: AbortSignal): Promise<AdminUser[]> {
        return http<AdminUser[]>(basePath, {}, signal)
    },

    create(body: CreateUserInput): Promise<AdminUser> {
        return http<AdminUser>(basePath, {
            method: 'POST',
            body: JSON.stringify(body),
        })
    },

    update(userId: number, body: UpdateUserInput): Promise<AdminUser> {
        return http<AdminUser>(`${basePath}/${userId}`, {
            method: 'PUT',
            body: JSON.stringify(body),
        })
    },

    remove(userId: number): Promise<void> {
        return http<void>(`${basePath}/${userId}`, {
            method: 'DELETE',
        })
    },
}