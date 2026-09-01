import type { ComplaintStatus, PassengerComplaintView, SafetyAlarmView } from '../domain/types'
import { apiRequest } from './http'

export function listComplaints(status?: ComplaintStatus): Promise<PassengerComplaintView[]> {
  const query = status ? `?status=${encodeURIComponent(status)}` : ''
  return apiRequest<PassengerComplaintView[]>(`/api/v1/admin/passenger-complaints${query}`)
}

export function handleComplaint(
  complaintNo: string,
  payload: { status: ComplaintStatus; note?: string },
): Promise<PassengerComplaintView> {
  return apiRequest<PassengerComplaintView>(
    `/api/v1/admin/passenger-complaints/${encodeURIComponent(complaintNo)}/handle`,
    { method: 'POST', body: JSON.stringify(payload) },
  )
}

export function listSafetyAlarms(): Promise<SafetyAlarmView[]> {
  return apiRequest<SafetyAlarmView[]>('/api/v1/admin/safety-alarms')
}
