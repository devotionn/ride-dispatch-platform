import type { PlatformBrand } from '../domain/types'
import { apiRequest } from './http'

export function getPublicBrand(): Promise<PlatformBrand> {
  return apiRequest<PlatformBrand>('/api/v1/public/brand')
}
