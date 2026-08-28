import type { PaymentExceptionView } from '../domain/types'
import { apiRequest } from './http'

function createIdempotencyKey(): string {
  const cryptoApi = globalThis.crypto
  if (cryptoApi?.randomUUID) return cryptoApi.randomUUID()
  if (cryptoApi?.getRandomValues) {
    const bytes = cryptoApi.getRandomValues(new Uint8Array(12))
    return `refund-${Date.now()}-${Array.from(bytes, (value) => value.toString(16).padStart(2, '0')).join('')}`
  }
  return `refund-${Date.now()}-${Math.random().toString(36).slice(2)}`
}

export function listPaymentExceptions(): Promise<PaymentExceptionView[]> {
  return apiRequest<PaymentExceptionView[]>('/api/v1/admin/payment-exceptions')
}

export function openPaymentException(payload: {
  paymentNo: string
  requestedAmount: number
  reason: string
}): Promise<PaymentExceptionView> {
  return apiRequest<PaymentExceptionView>('/api/v1/admin/payment-exceptions', {
    method: 'POST',
    headers: { 'Idempotency-Key': createIdempotencyKey() },
    body: JSON.stringify(payload),
  })
}

export function resolvePaymentException(exceptionNo: string, payload: { externalRefundRef: string; note: string }): Promise<PaymentExceptionView> {
  return apiRequest<PaymentExceptionView>(`/api/v1/admin/payment-exceptions/${exceptionNo}/resolve`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function rejectPaymentException(exceptionNo: string, note: string): Promise<PaymentExceptionView> {
  return apiRequest<PaymentExceptionView>(`/api/v1/admin/payment-exceptions/${exceptionNo}/reject`, {
    method: 'POST',
    body: JSON.stringify({ note }),
  })
}
