import type { CreateOrderPayload } from '../domain/types'

const STORAGE_KEY = 'ride-dispatch:pending-order-idempotency'
const MAX_AGE_MS = 10 * 60 * 1000

interface PendingIdempotency {
  key: string
  fingerprint: string
  createdAt: number
}

export async function getOrCreateOrderIdempotencyKey(payload: CreateOrderPayload): Promise<string> {
  const fingerprint = await fingerprintPayload(payload)
  const existing = readPending()
  const now = Date.now()
  if (existing && existing.fingerprint === fingerprint && now - existing.createdAt <= MAX_AGE_MS) {
    return existing.key
  }

  const key = generateKey()
  const pending: PendingIdempotency = { key, fingerprint, createdAt: now }
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(pending))
  } catch {
    // The in-memory key still protects the current submit when storage is unavailable.
  }
  return key
}

export function clearOrderIdempotencyKey(key: string): void {
  const existing = readPending()
  if (!existing || existing.key !== key) return
  try {
    sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // Ignore storage failures after a successful order creation.
  }
}

function readPending(): PendingIdempotency | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<PendingIdempotency>
    if (typeof parsed.key !== 'string' || typeof parsed.fingerprint !== 'string' || typeof parsed.createdAt !== 'number') {
      return null
    }
    return parsed as PendingIdempotency
  } catch {
    return null
  }
}

async function fingerprintPayload(payload: CreateOrderPayload): Promise<string> {
  const bytes = new TextEncoder().encode(JSON.stringify(payload))
  if (crypto.subtle) {
    const digest = await crypto.subtle.digest('SHA-256', bytes)
    return Array.from(new Uint8Array(digest), (value) => value.toString(16).padStart(2, '0')).join('')
  }
  return Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('').slice(0, 128)
}

function generateKey(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bytes = new Uint8Array(24)
  crypto.getRandomValues(bytes)
  return Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('')
}
