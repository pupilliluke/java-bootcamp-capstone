export type ApiErrorKind = 'network' | 'http' | 'abort' | 'parse'

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly kind: ApiErrorKind,
    public readonly status?: number,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}
