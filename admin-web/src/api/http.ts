import { clearSession, getSession } from '../storage/auth'

export class ApiRequestError extends Error {
  readonly code: string
  readonly status: number
  readonly requestId?: string

  constructor(message: string, code: string, status: number, requestId?: string) {
    super(message)
    this.name = 'ApiRequestError'
    this.code = code
    this.status = status
    this.requestId = requestId
  }
}

const apiBase = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

export async function apiRequest<T>(path: string, init: RequestInit = {}, authenticated = true): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type') && !(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }

  if (authenticated) {
    const session = getSession()
    if (!session) {
      clearSession()
      window.dispatchEvent(new Event('admin-auth-expired'))
      throw new ApiRequestError('登录已失效，请重新登录', 'AUTH_REQUIRED', 401)
    }
    headers.set('Authorization', `Bearer ${session.accessToken}`)
  }

  const response = await fetch(`${apiBase}${path}`, { ...init, headers })
  if (!response.ok) {
    let message = `请求失败（${response.status}）`
    let code = 'HTTP_ERROR'
    let requestId: string | undefined
    try {
      const body = (await response.json()) as { code?: string; message?: string; requestId?: string }
      message = body.message || message
      code = body.code || code
      requestId = body.requestId
    } catch {
      // Keep generic HTTP error for non-JSON responses.
    }
    if (response.status === 401) {
      clearSession()
      window.dispatchEvent(new Event('admin-auth-expired'))
    }
    throw new ApiRequestError(message, code, response.status, requestId)
  }

  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}
