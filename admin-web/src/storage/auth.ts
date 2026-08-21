import type { AdminSession } from '../domain/types'

const STORAGE_KEY = 'ride-dispatch:admin-session'

export function saveSession(session: AdminSession): void {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
}

export function getSession(): AdminSession | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as Partial<AdminSession>
    if (!session.accessToken || !session.expiresAt || !session.authority) return null
    if (Date.parse(session.expiresAt) <= Date.now()) {
      clearSession()
      return null
    }
    return session as AdminSession
  } catch {
    return null
  }
}

export function clearSession(): void {
  localStorage.removeItem(STORAGE_KEY)
}

export function isAuthenticated(): boolean {
  return getSession() !== null
}
