import type {
  AdminCreateOrderPayload,
  NearbyDriver,
  OrderDetail,
  OrderStatus,
  PagedOrders,
} from '../domain/types'
import { apiRequest } from './http'

export function listOrders(status?: OrderStatus, page = 0, size = 50): Promise<PagedOrders> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (status) params.set('status', status)
  return apiRequest<PagedOrders>(`/api/v1/admin/orders?${params.toString()}`)
}

export function getOrderDetail(orderNo: string): Promise<OrderDetail> {
  return apiRequest<OrderDetail>(`/api/v1/admin/orders/${encodeURIComponent(orderNo)}`)
}

export function createAdminOrder(payload: AdminCreateOrderPayload): Promise<{ orderNo: string; status: OrderStatus; passengerAccessToken: string }> {
  return apiRequest('/api/v1/admin/orders', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function listNearbyDrivers(orderNo: string): Promise<NearbyDriver[]> {
  return apiRequest<NearbyDriver[]>(`/api/v1/admin/orders/${encodeURIComponent(orderNo)}/nearby-drivers`)
}

export function dispatchOrder(orderNo: string, driverId: number): Promise<{ attemptId: number; targetDriverId: number; status: string }> {
  return apiRequest(`/api/v1/admin/orders/${encodeURIComponent(orderNo)}/dispatch`, {
    method: 'POST',
    body: JSON.stringify({ driverId }),
  })
}

export function reassignOrder(orderNo: string, driverId: number, reason?: string): Promise<{ attemptId: number; targetDriverId: number; status: string }> {
  return apiRequest(`/api/v1/admin/orders/${encodeURIComponent(orderNo)}/reassign`, {
    method: 'POST',
    body: JSON.stringify({ driverId, reason: reason || undefined }),
  })
}

export function forceCancelOrder(orderNo: string, reason: string): Promise<{ status: OrderStatus }> {
  return apiRequest(`/api/v1/admin/orders/${encodeURIComponent(orderNo)}/force-cancel`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  })
}

export function cancelPendingOrder(orderNo: string, reason: string): Promise<OrderStatus> {
  return apiRequest<OrderStatus>(`/api/v1/admin/orders/${encodeURIComponent(orderNo)}/cancel`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  })
}

export function markOrderException(orderNo: string, reason: string): Promise<OrderStatus> {
  return apiRequest<OrderStatus>(`/api/v1/admin/orders/${encodeURIComponent(orderNo)}/mark-exception`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  })
}

export function forceReassignOrder(orderNo: string, driverId: number, reason: string): Promise<{ attemptId: number; targetDriverId: number; status: string }> {
  return apiRequest(`/api/v1/admin/orders/${encodeURIComponent(orderNo)}/force-reassign`, {
    method: 'POST',
    body: JSON.stringify({ driverId, reason }),
  })
}
