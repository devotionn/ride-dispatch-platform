import type { LoginResponse } from '../domain/types'
import { clearSession, getSession, saveSession } from '../storage/auth'
import { apiRequest } from './http'

export async function loginAdmin(username: string, password: string): Promise<LoginResponse> {
  const result = await apiRequest<LoginResponse>('/api/v1/auth/admin/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  }, false)
  saveSession(result)
  return result
}

export async function logoutAdmin(): Promise<void> {
  const session = getSession()
  try {
    if (session) {
      await apiRequest<void>('/api/v1/auth/logout', { method: 'POST' })
    }
  } finally {
    clearSession()
  }
}
