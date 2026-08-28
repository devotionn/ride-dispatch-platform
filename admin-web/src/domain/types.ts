export type AdminAuthority = 'ROLE_ADMIN' | 'ROLE_DISPATCHER' | 'ROLE_FINANCE'
export type OrderSourceType = 'PUBLIC_H5' | 'DRIVER_QR' | 'ADMIN_CREATED'
export type OrderStatus =
  | 'PENDING_DISPATCH'
  | 'PENDING_DRIVER_CONFIRM'
  | 'ACCEPTED'
  | 'IN_SERVICE'
  | 'PENDING_PAYMENT'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'EXCEPTION'
export type TripStage = 'ARRIVED_PICKUP' | 'PASSENGER_ONBOARD' | 'IN_TRANSIT' | 'ARRIVED_DESTINATION'

export interface LoginResponse {
  accessToken: string
  expiresAt: string
  authority: AdminAuthority
}

export interface AdminSession extends LoginResponse {}

export interface DriverView {
  id: number
  driverNo: string
  name: string
  mobile: string
  accountStatus: 'ACTIVE' | 'DISABLED'
  workStatus: 'AVAILABLE' | 'PAUSED' | 'OFFLINE'
  maxPassengers: number
  availablePassengers: number
  qrShortCode: string
  vehicleId?: number | null
  plateNo?: string | null
  brandModel?: string | null
}

export interface OrderSummary {
  orderNo: string
  sourceType: OrderSourceType
  status: OrderStatus
  currentDriverId?: number | null
  pickupAddress: string
  destinationAddress: string
  passengerCount: number
  departureAt: string
  createdAt: string
}

export interface PagedOrders {
  content: OrderSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface OrderView extends OrderSummary {
  sourceDriverId?: number | null
  passengerMobile: string
  pickupLatitude: number
  pickupLongitude: number
  destinationLatitude: number
  destinationLongitude: number
  remark?: string | null
  tripStage?: TripStage | null
  finalAmount?: number | null
  acceptedAt?: string | null
  serviceStartedAt?: string | null
  arrivedDestinationAt?: string | null
  cancelledAt?: string | null
  updatedAt: string
}

export interface DispatchAttemptView {
  attemptId: number
  targetDriverId: number
  dispatchType: 'DIRECT_QR' | 'MANUAL' | 'REASSIGN' | 'FORCE_REASSIGN'
  status: 'WAITING' | 'ACCEPTED' | 'REJECTED' | 'CANCELLED_BY_REASSIGN' | 'CANCELLED_BY_ORDER'
  dispatchedBy?: number | null
  dispatchedAt: string
  respondedAt?: string | null
  rejectReasonCode?: string | null
  rejectReasonText?: string | null
}

export interface ProgressEventView {
  id: number
  driverId: number
  stage: TripStage
  occurredAt: string
}

export interface OrderDetail {
  order: OrderView
  dispatchAttempts: DispatchAttemptView[]
  progressEvents: ProgressEventView[]
  operationLogs: OperationLogView[]
}

export interface NearbyDriver {
  driverId: number
  driverNo: string
  driverName: string
  availablePassengers: number
  straightLineDistanceKm: number
  locatedAt: string
}

export interface GeoPointPayload {
  address: string
  latitude: number
  longitude: number
}

export interface AdminCreateOrderPayload {
  pickup: GeoPointPayload
  destination: GeoPointPayload
  passengerCount: number
  departureAt: string
  mobile: string
  remark?: string
}

export interface PlatformBrand {
  companyName: string
  logoUrl?: string | null
  updatedAt: string
  updatedBy?: number | null
}

export interface OperationLogView {
  id: number
  operatorType: string
  operatorId?: number | null
  objectType?: string
  objectId?: string
  action: string
  beforeJson?: string | null
  afterJson?: string | null
  reason?: string | null
  requestId?: string | null
  createdAt: string
}

export interface PaymentAttemptView {
  attemptNo: string
  channel: string
  amount: number
  status: string
  merchantOrderNo: string
  thirdPartyTransactionNo?: string | null
  createdAt: string
  paidAt?: string | null
}

export interface PaymentView {
  paymentNo: string
  orderId: number
  amount: number
  status: string
  settlementMethod?: string | null
  attempts: PaymentAttemptView[]
}

export type PaymentExceptionStatus = 'OPEN' | 'RESOLVED' | 'REJECTED'

export interface PaymentExceptionView {
  exceptionNo: string
  paymentNo?: string | null
  paymentId: number
  orderId: number
  requestedAmount: number
  status: PaymentExceptionStatus
  reason: string
  externalRefundRef?: string | null
  resolutionNote?: string | null
  createdBy?: number | null
  resolvedBy?: number | null
  createdAt: string
  resolvedAt?: string | null
  updatedAt: string
}

export interface WithdrawalView {
  withdrawalNo: string
  amount: number
  channel: string
  account: string
  status: string
  reason?: string | null
  createdAt: string
  reviewedAt?: string | null
  paidAt?: string | null
}
