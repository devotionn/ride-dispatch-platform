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

export type TripStage =
  | 'ARRIVED_PICKUP'
  | 'PASSENGER_ONBOARD'
  | 'IN_TRANSIT'
  | 'ARRIVED_DESTINATION'

export interface PlatformBrand {
  companyName: string
  logoUrl?: string | null
}

export interface PublicDriverProfile {
  name: string
  plateNo?: string | null
  brandModel?: string | null
  maxPassengers: number
}

export type PlaceSource = 'BROWSER_LOCATION' | 'PLACE_CATALOG' | 'MANUAL' | 'ADMIN'

export interface GeoPointPayload {
  address: string
  latitude?: number | null
  longitude?: number | null
  coordinateSystem?: 'WGS84'
  source?: PlaceSource
  accuracyMeters?: number | null
  placeId?: number | null
}

export interface CreateOrderPayload {
  sourceType: 'PUBLIC_H5' | 'DRIVER_QR'
  driverShortCode?: string
  pickup: GeoPointPayload
  destination: GeoPointPayload
  passengerCount: number
  departureAt: string
  mobile: string
  remark?: string
}

export interface CreateOrderResponse {
  orderNo: string
  status: OrderStatus
  passengerAccessToken: string
}

export interface PassengerOrder {
  orderNo: string
  sourceType: OrderSourceType
  status: OrderStatus
  tripStage?: TripStage | null
  currentDriverId?: number | null
  pickupAddress: string
  pickupLatitude?: number | null
  pickupLongitude?: number | null
  destinationAddress: string
  destinationLatitude?: number | null
  destinationLongitude?: number | null
  passengerCount: number
  departureAt: string
  remark?: string | null
  finalAmount?: number | null
  acceptedAt?: string | null
  serviceStartedAt?: string | null
  arrivedDestinationAt?: string | null
  createdAt: string
  paymentToken?: string | null
  paymentStatus?: 'PENDING' | 'PAID' | 'EXPIRED' | 'CANCELLED' | null
}
