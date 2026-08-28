import type { WithdrawalView } from '../domain/types'
import { apiRequest } from './http'

export function listWithdrawals(): Promise<WithdrawalView[]> {
  return apiRequest<WithdrawalView[]>('/api/v1/admin/withdrawals')
}

export function approveWithdrawal(withdrawalNo: string): Promise<WithdrawalView> {
  return apiRequest<WithdrawalView>(`/api/v1/admin/withdrawals/${encodeURIComponent(withdrawalNo)}/approve`, { method: 'POST' })
}

export function rejectWithdrawal(withdrawalNo: string, reason: string): Promise<WithdrawalView> {
  return apiRequest<WithdrawalView>(`/api/v1/admin/withdrawals/${encodeURIComponent(withdrawalNo)}/reject`, {
    method: 'POST', body: JSON.stringify({ reason }),
  })
}

export function markWithdrawalPaid(withdrawalNo: string): Promise<WithdrawalView> {
  return apiRequest<WithdrawalView>(`/api/v1/admin/withdrawals/${encodeURIComponent(withdrawalNo)}/mark-paid`, { method: 'POST' })
}
