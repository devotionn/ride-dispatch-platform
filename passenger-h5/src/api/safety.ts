import { apiRequest } from './http'

export interface SafetyAlarmPayload {
  orderNo?: string
  passengerToken?: string
  sourcePage: string
  latitude?: number | null
  longitude?: number | null
  locationText?: string
}

export interface SafetyAlarmResult {
  alarmId: number
  createdAt: string
}

export interface ComplaintPayload {
  category: string
  description: string
  contactMobile?: string
}

export interface ComplaintResult {
  complaintNo: string
  status: string
}

export async function reportSafetyAlarm(payload: SafetyAlarmPayload): Promise<SafetyAlarmResult | null> {
  try {
    return await apiRequest<SafetyAlarmResult>('/api/v1/public/safety/alarms', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  } catch {
    // The call to 110 must never depend on the alarm record succeeding.
    return null
  }
}

export function submitComplaint(orderNo: string, token: string, payload: ComplaintPayload): Promise<ComplaintResult> {
  return apiRequest<ComplaintResult>(
    `/api/v1/public/safety/orders/${encodeURIComponent(orderNo)}/complaints`,
    { method: 'POST', headers: { 'X-Passenger-Token': token }, body: JSON.stringify(payload) },
  )
}
