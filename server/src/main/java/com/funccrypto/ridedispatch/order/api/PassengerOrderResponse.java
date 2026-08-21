package com.funccrypto.ridedispatch.order.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.funccrypto.ridedispatch.order.OrderSourceType;
import com.funccrypto.ridedispatch.order.OrderStatus;
import com.funccrypto.ridedispatch.order.RideOrderEntity;
import com.funccrypto.ridedispatch.order.TripStage;

public record PassengerOrderResponse(
        String orderNo,
        OrderSourceType sourceType,
        OrderStatus status,
        TripStage tripStage,
        Long currentDriverId,
        String pickupAddress,
        BigDecimal pickupLatitude,
        BigDecimal pickupLongitude,
        String destinationAddress,
        BigDecimal destinationLatitude,
        BigDecimal destinationLongitude,
        int passengerCount,
        Instant departureAt,
        String remark,
        Long finalAmount,
        Instant acceptedAt,
        Instant serviceStartedAt,
        Instant arrivedDestinationAt,
        Instant createdAt) {

    public static PassengerOrderResponse from(RideOrderEntity order) {
        return new PassengerOrderResponse(
                order.getOrderNo(), order.getSourceType(), order.getStatus(), order.getTripStage(),
                order.getCurrentDriverId(), order.getPickupAddress(), order.getPickupLatitude(), order.getPickupLongitude(),
                order.getDestinationAddress(), order.getDestinationLatitude(), order.getDestinationLongitude(),
                order.getPassengerCount(), order.getDepartureAt(), order.getRemark(), order.getFinalAmount(),
                order.getAcceptedAt(), order.getServiceStartedAt(), order.getArrivedDestinationAt(), order.getCreatedAt());
    }
}
