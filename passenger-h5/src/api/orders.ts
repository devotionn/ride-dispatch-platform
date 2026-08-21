import type { CreateOrderPayload, CreateOrderResponse, OrderStatus, PassengerOrder } from '../domain/types'
import { apiRequest } from './http'

const passengerHeader = (token: string) => ({ 'X-Passenger-Token': token })

export function createOrder(payload: CreateOrderPayload): Promise<CreateOrderResponse> {
  return apiRequest<CreateOrderResponse>('/api/v1/public/orders', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
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
