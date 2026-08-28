import type { PaymentView } from '../domain/types'
import { apiRequest } from './http'

export function listPayments(): Promise<PaymentView[]> {
  return apiRequest<PaymentView[]>('/api/v1/admin/payments')
}

export function adjustOfflinePayment(paymentNo: string, deltaAmount: number, reason: string): Promise<unknown> {
  return apiRequest(`/api/v1/admin/payments/${encodeURIComponent(paymentNo)}/offline-adjustments`, {
    method: 'POST',
    headers: { 'Idempotency-Key': `offline-adjust-${crypto.randomUUID()}` },
    body: JSON.stringify({ deltaAmount, reason }),
  })
}
