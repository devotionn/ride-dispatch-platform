import type { DriverView } from '../domain/types'
import { apiRequest } from './http'

export interface CreateDriverPayload {
  driverNo: string
  name: string
  mobile: string
  password: string
  maxPassengers: number
  availablePassengers: number
  plateNo: string
  brandModel?: string
}

export function listDrivers(): Promise<DriverView[]> {
  return apiRequest<DriverView[]>('/api/v1/admin/drivers')
}

export function createDriver(payload: CreateDriverPayload): Promise<DriverView> {
  return apiRequest<DriverView>('/api/v1/admin/drivers', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export interface DriverOrderSummary {
  orderNo: string
  status: string
  createdAt: string
  finalAmount: number | null
}

export interface DriverWithdrawalSummary {
  withdrawalNo: string
  amount: number
  channel: string
  account: string
  status: string
  createdAt: string
}

export interface DriverDetailView {
  driver: DriverView
  activeOrders: DriverOrderSummary[]
  historyOrders: DriverOrderSummary[]
  completedOrderCount: number
  businessIncome: number
  availableBalance: number
  withdrawals: DriverWithdrawalSummary[]
}

export function getDriverDetail(id: number): Promise<DriverDetailView> {
  return apiRequest<DriverDetailView>(`/api/v1/admin/drivers/${id}`)
}

export interface UpdateDriverPayload {
  name: string
  mobile: string
  password?: string
  maxPassengers: number
  availablePassengers: number
  plateNo: string
  brandModel?: string
}

export function updateDriver(id: number, payload: UpdateDriverPayload): Promise<DriverView> {
  return apiRequest<DriverView>(`/api/v1/admin/drivers/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function updateDriverStatus(id: number, accountStatus: 'ACTIVE' | 'DISABLED'): Promise<DriverView> {
  return apiRequest<DriverView>(`/api/v1/admin/drivers/${id}/status`, {
    method: 'PUT',
    body: JSON.stringify({ accountStatus }),
  })
}

export interface DriverQrView {
  driverId: number
  driverNo: string
  shortCode: string
  path: string
  imageDataUrl: string
}

export function getDriverQr(id: number): Promise<DriverQrView> {
  return apiRequest<DriverQrView>(`/api/v1/admin/drivers/${id}/qr`)
}
