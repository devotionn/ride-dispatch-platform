import { apiRequest } from './http'

export type PaymentStatus = 'PENDING' | 'PAID' | 'EXPIRED' | 'CANCELLED'
export type PaymentChannel = 'MOCK_WECHAT' | 'MOCK_ALIPAY'
export type PaymentAttemptStatus = 'CREATED' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'IGNORED_ALREADY_SETTLED'

export interface PaymentAttempt {
  attemptNo: string
  channel: PaymentChannel
  amount: number
  status: PaymentAttemptStatus
  thirdPartyTransactionNo?: string | null
  createdAt: string
  paidAt?: string | null
}

export interface PaymentResponse {
  paymentNo: string
  orderId: number
  amount: number
  status: PaymentStatus
  settlementMethod?: string | null
  attempts: PaymentAttempt[]
}

function paymentPath(token: string, suffix = ''): string {
  return '/api/v1/public/payments/' + encodeURIComponent(token) + suffix
}

export function getPayment(token: string): Promise<PaymentResponse> {
  return apiRequest<PaymentResponse>(paymentPath(token))
}

export function getPaymentStatus(token: string): Promise<PaymentResponse> {
  return apiRequest<PaymentResponse>(paymentPath(token, '/status'))
}

export function createPaymentAttempt(token: string, channel: PaymentChannel): Promise<PaymentAttempt> {
  return apiRequest<PaymentAttempt>(paymentPath(token, '/attempts'), {
    method: 'POST',
    headers: { 'Idempotency-Key': `payment-attempt-${crypto.randomUUID()}` },
    body: JSON.stringify({ channel }),
  })
}

export function mockPaymentSuccess(attemptNo: string): Promise<PaymentAttempt> {
  return apiRequest<PaymentAttempt>('/api/v1/local/mock-payments/' + encodeURIComponent(attemptNo) + '/success', {
    method: 'POST',
    body: JSON.stringify({}),
  })
}

export function mockPaymentFailure(attemptNo: string): Promise<PaymentAttempt> {
  return apiRequest<PaymentAttempt>('/api/v1/local/mock-payments/' + encodeURIComponent(attemptNo) + '/failure', {
    method: 'POST',
  })
}
