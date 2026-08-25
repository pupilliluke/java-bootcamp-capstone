export type UserRole = 'AGENT' | 'ADMIN'

export interface AdminUser {
    id: number
    username: string
    email: string
    role: UserRole
    enabled: boolean
    createdAt: string
}

export interface CreateUserInput {
    username: string
    email: string
    password: string
    role: UserRole
}

export interface UpdateUserInput {
    email: string
    newPassword: string
    role: UserRole
    enabled: boolean
}