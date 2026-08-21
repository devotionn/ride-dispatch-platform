export class ApiRequestError extends Error {
  readonly code: string
  readonly requestId?: string
  readonly status: number

  constructor(message: string, code: string, status: number, requestId?: string) {
    super(message)
    this.name = 'ApiRequestError'
    this.code = code
    this.status = status
    this.requestId = requestId
  }
}

const apiBase = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${apiBase}${path}`, {
    ...init,
    headers,
  })

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
      // Keep the generic HTTP error when the response is not JSON.
    }
    throw new ApiRequestError(message, code, response.status, requestId)
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}
