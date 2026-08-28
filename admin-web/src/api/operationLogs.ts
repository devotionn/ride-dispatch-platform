import type { OperationLogView } from '../domain/types'
import { apiRequest } from './http'

export interface PagedOperationLogs {
  content: OperationLogView[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface OperationLogFilters {
  operatorType?: string
  objectType?: string
  objectId?: string
  action?: string
  page?: number
  size?: number
}

export function listOperationLogs(filters: OperationLogFilters = {}): Promise<PagedOperationLogs> {
  const params = new URLSearchParams({ page: String(filters.page ?? 0), size: String(filters.size ?? 100) })
  for (const key of ['operatorType', 'objectType', 'objectId', 'action'] as const) {
    if (filters[key]?.trim()) params.set(key, filters[key]!.trim())
  }
  return apiRequest<PagedOperationLogs>(`/api/v1/admin/operation-logs?${params.toString()}`)
}
