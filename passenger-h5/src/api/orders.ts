import type { CreateOrderPayload, CreateOrderResponse, OrderStatus, PassengerOrder } from '../domain/types'
import { ApiRequestError, apiRequest } from './http'

const passengerHeader = (token: string) => ({ 'X-Passenger-Token': token })

export async function createOrder(payload: CreateOrderPayload, idempotencyKey: string): Promise<CreateOrderResponse> {
  for (let attempt = 0; attempt < 2; attempt += 1) {
    try {
      return await apiRequest<CreateOrderResponse>('/api/v1/public/orders', {
        method: 'POST',
        headers: { 'Idempotency-Key': idempotencyKey },
        body: JSON.stringify(payload),
      })
    } catch (error) {
      const retryable = !(error instanceof ApiRequestError) || error.status === 409 || error.status >= 500
      if (attempt === 0 && retryable) {
        await new Promise((resolve) => window.setTimeout(resolve, 350))
        continue
      }
      throw error
    }
  }
  throw new Error('订单提交失败，请稍后重试')
}

export function getPassengerOrder(orderNo: string, token: string): Promise<PassengerOrder> {
  return apiRequest<PassengerOrder>(`/api/v1/public/orders/${encodeURIComponent(orderNo)}`, {
    headers: passengerHeader(token),
  })
}

export function cancelPassengerOrder(orderNo: string, token: string): Promise<OrderStatus> {
  return apiRequest<OrderStatus>(`/api/v1/public/orders/${encodeURIComponent(orderNo)}/cancel`, {
    method: 'POST',
    headers: passengerHeader(token),
  })
}
