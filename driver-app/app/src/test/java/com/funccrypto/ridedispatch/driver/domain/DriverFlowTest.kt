package com.funccrypto.ridedispatch.driver.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DriverFlowTest {

    @Test
    fun tripStagesAdvanceOnlyAlongTheServerDefinedSequence() {
        assertEquals(TripStage.ARRIVED_PICKUP, nextTripStage(null))
        assertEquals(TripStage.PASSENGER_ONBOARD, nextTripStage(TripStage.ARRIVED_PICKUP))
        assertEquals(TripStage.IN_TRANSIT, nextTripStage(TripStage.PASSENGER_ONBOARD))
        assertEquals(TripStage.ARRIVED_DESTINATION, nextTripStage(TripStage.IN_TRANSIT))
        assertNull(nextTripStage(TripStage.ARRIVED_DESTINATION))
    }

    @Test
    fun finalAmountEditorIsNotAvailableAfterAmountSubmission() {
        val order = DriverOrder(
            orderNo = "RDTEST",
            status = OrderStatus.PENDING_PAYMENT,
            tripStage = TripStage.ARRIVED_DESTINATION,
            passengerMobile = "13800000000",
            pickupAddress = "扬州东站",
            pickupLatitude = null,
            pickupLongitude = null,
            destinationAddress = "瘦西湖",
            destinationLatitude = null,
            destinationLongitude = null,
            passengerCount = 1,
            departureAt = "2026-08-24T00:00:00Z",
            remark = null,
            finalAmount = 1200,
        )

        assertFalse(order.canSubmitFinalAmount())
    }

    @Test
    fun driverAmountUsesYuanButApiValueUsesFen() {
        assertEquals("12.00", formatFenAsYuan(1200))
        assertEquals("1200.00", formatFenAsYuan(120000))
        assertEquals(120000L, parseYuanToFen("1200"))
        assertEquals(120050L, parseYuanToFen("1200.50"))
        assertNull(parseYuanToFen("1200.005"))
        assertNull(parseYuanToFen("0"))
    }
}
