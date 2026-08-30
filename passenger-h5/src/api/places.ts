import { apiRequest } from './http'

export interface PlaceCatalogItem {
  id: number
  name: string
  addressText: string
  latitude?: number | null
  longitude?: number | null
  coordinateSystem: 'WGS84' | string
  city?: string | null
  district?: string | null
  category?: string | null
  aliases?: string | null
}

export async function searchPlaces(query: string, limit = 10): Promise<PlaceCatalogItem[]> {
  const q = query.trim()
  if (q.length < 2) return []
  return apiRequest<PlaceCatalogItem[]>(
    `/api/v1/public/places/search?q=${encodeURIComponent(q)}&limit=${Math.max(1, Math.min(limit, 20))}`,
  )
}
