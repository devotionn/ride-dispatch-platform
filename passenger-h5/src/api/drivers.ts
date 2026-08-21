import type { PublicDriverProfile } from '../domain/types'
import { apiRequest } from './http'

export function getPublicDriver(shortCode: string): Promise<PublicDriverProfile> {
  return apiRequest<PublicDriverProfile>(`/api/v1/public/drivers/${encodeURIComponent(shortCode)}`)
}
