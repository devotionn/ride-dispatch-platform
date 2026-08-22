import type { PlatformBrand } from '../domain/types'
import { apiRequest } from './http'

export function getAdminBrand(): Promise<PlatformBrand> {
  return apiRequest<PlatformBrand>('/api/v1/admin/brand')
}

export function updateAdminBrand(payload: { companyName: string; logoUrl?: string }): Promise<PlatformBrand> {
  return apiRequest<PlatformBrand>('/api/v1/admin/brand', {
    method: 'PUT',
    body: JSON.stringify({
      companyName: payload.companyName,
      logoUrl: payload.logoUrl?.trim() || undefined,
    }),
  })
}
