package com.funccrypto.ridedispatch.driver.domain

import java.math.BigDecimal
import java.math.RoundingMode

enum class WorkStatus {
    AVAILABLE,
    PAUSED,
    OFFLINE,
}

enum class OrderStatus {
    PENDING_DISPATCH,
    PENDING_DRIVER_CONFIRM,
    ACCEPTED,
    IN_SERVICE,
    PENDING_PAYMENT,
    COMPLETED,
    CANCELLED,
    UNKNOWN,
}

enum class TripStage {
    ARRIVED_PICKUP,
    PASSENGER_ONBOARD,
    IN_TRANSIT,
    ARRIVED_DESTINATION,
}

fun nextTripStage(current: TripStage?): TripStage? = when (current) {
    null -> TripStage.ARRIVED_PICKUP
    TripStage.ARRIVED_PICKUP -> TripStage.PASSENGER_ONBOARD
    TripStage.PASSENGER_ONBOARD -> TripStage.IN_TRANSIT
    TripStage.IN_TRANSIT -> TripStage.ARRIVED_DESTINATION
    TripStage.ARRIVED_DESTINATION -> null
}

fun DriverOrder.canSubmitFinalAmount(): Boolean =
    status == OrderStatus.IN_SERVICE &&
        tripStage == TripStage.ARRIVED_DESTINATION &&
        finalAmount == null

/**
 * The API/database use fen (the smallest currency unit), while driver-facing
 * screens use yuan. Keep the conversion at the UI boundary so money remains
 * an integer throughout the service and payment layers.
 */
fun formatFenAsYuan(fen: Long): String =
    BigDecimal.valueOf(fen).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY).toPlainString()

fun parseYuanToFen(input: String): Long? = runCatching {
    BigDecimal(input.trim())
        .movePointRight(2)
        .setScale(0, RoundingMode.UNNECESSARY)
        .longValueExact()
}.getOrNull()?.takeIf { it > 0 }

fun String?.toOrderStatus(): OrderStatus = runCatching {
    OrderStatus.valueOf(this.orEmpty())
}.getOrDefault(OrderStatus.UNKNOWN)

fun String?.toTripStage(): TripStage? = this?.let { value ->
    runCatching { TripStage.valueOf(value) }.getOrNull()
}

data class DriverSession(
    val accessToken: String,
    val expiresAt: String,
    val authority: String,
)

data class DriverState(
    val driverId: Long,
    val workStatus: WorkStatus,
    val availablePassengers: Int,
    val maxPassengers: Int,
) {
    companion object
}

data class DriverOrder(
    val orderNo: String,
    val status: OrderStatus,
    val tripStage: TripStage?,
    val passengerMobile: String,
    val pickupAddress: String,
    val pickupLatitude: Double?,
    val pickupLongitude: Double?,
    val destinationAddress: String,
    val destinationLatitude: Double?,
    val destinationLongitude: Double?,
    val passengerCount: Int,
    val departureAt: String,
    val remark: String?,
    val finalAmount: Long?,
) {
    companion object
}

data class PendingDispatch(
    val attemptId: Long,
    val dispatchedAt: String,
    val order: DriverOrder,
)

data class LocationSnapshot(
    val latitude: Double,
    val longitude: Double,
    val locatedAt: String,
)

data class DriverAccount(
    val businessIncome: Long,
    val availableBalance: Long,
    val frozenBalance: Long,
)

data class DriverProfile(
    val driverNo: String,
    val name: String,
    val mobile: String,
    val plateNo: String?,
    val brandModel: String?,
)

data class DriverQr(val shortCode: String, val path: String, val imageDataUrl: String?)

data class LedgerItem(val ledgerType: String, val amount: Long, val createdAt: String)
