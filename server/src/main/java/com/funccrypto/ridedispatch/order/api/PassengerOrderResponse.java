package com.funccrypto.ridedispatch.order.api;

import java.time.Instant;

import com.funccrypto.ridedispatch.order.OrderSourceType;
import com.funccrypto.ridedispatch.order.OrderStatus;
import com.funccrypto.ridedispatch.order.RideOrderEntity;

public record PassengerOrderResponse(
        String orderNo,
        OrderSourceType sourceType,
        OrderStatus status,
        Long currentDriverId,
        int passengerCount,
        Instant departureAt,
        Instant createdAt) {

    public static PassengerOrderResponse from(RideOrderEntity order) {
        return new PassengerOrderResponse(
                order.getOrderNo(),
                order.getSourceType(),
                order.getStatus(),
                order.getCurrentDriverId(),
                order.getPassengerCount(),
                order.getDepartureAt(),
                order.getCreatedAt());
    }
}
