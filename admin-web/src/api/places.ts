import { apiRequest } from './http'

export interface PlaceCatalogItem {
  id: number
  name: string
  addressText: string
  latitude?: number | null
  longitude?: number | null
  coordinateSystem: string
  city?: string | null
  district?: string | null
  category?: string | null
  aliases?: string | null
  enabled: boolean
  usageCount: number
  lastUsedAt?: string | null
}

export async function searchPublicPlaces(query: string, limit = 10): Promise<PlaceCatalogItem[]> {
  const q = query.trim()
  if (q.length < 2) return []
  return apiRequest<PlaceCatalogItem[]>(
    `/api/v1/public/places/search?q=${encodeURIComponent(q)}&limit=${Math.max(1, Math.min(limit, 20))}`,
    {},
    false,
  )
}

export function listPlaces(): Promise<PlaceCatalogItem[]> {
  return apiRequest<PlaceCatalogItem[]>('/api/v1/admin/places')
}

export interface PlaceCatalogPayload {
  name: string
  addressText: string
  latitude?: number | null
  longitude?: number | null
  city?: string
  district?: string
  category?: string
  aliases?: string
}

export function createPlace(payload: PlaceCatalogPayload): Promise<PlaceCatalogItem> {
  return apiRequest<PlaceCatalogItem>('/api/v1/admin/places', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function updatePlace(id: number, payload: PlaceCatalogPayload): Promise<PlaceCatalogItem> {
  return apiRequest<PlaceCatalogItem>(`/api/v1/admin/places/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

export function setPlaceEnabled(id: number, enabled: boolean): Promise<PlaceCatalogItem> {
  return apiRequest<PlaceCatalogItem>(`/api/v1/admin/places/${id}/enabled`, {
    method: 'PATCH',
    body: JSON.stringify({ enabled }),
  })
}
