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
