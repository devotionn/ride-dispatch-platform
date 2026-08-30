package com.funccrypto.ridedispatch.order.api;

import java.math.BigDecimal;
import java.time.Instant;

import com.funccrypto.ridedispatch.driver.DriverEntity;
import com.funccrypto.ridedispatch.driver.VehicleEntity;
import com.funccrypto.ridedispatch.order.OrderSourceType;
import com.funccrypto.ridedispatch.order.OrderStatus;
import com.funccrypto.ridedispatch.order.RideOrderEntity;
import com.funccrypto.ridedispatch.order.TripStage;
import com.funccrypto.ridedispatch.payment.PaymentEntity;
import com.funccrypto.ridedispatch.payment.PaymentStatus;

public record PassengerOrderResponse(
        String orderNo,
        OrderSourceType sourceType,
        OrderStatus status,
        TripStage tripStage,
        Long currentDriverId,
        String driverName,
        String driverNo,
        String vehiclePlateNo,
        String vehicleBrandModel,
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
        Instant createdAt,
        String paymentToken,
        PaymentStatus paymentStatus) {

    public static PassengerOrderResponse from(RideOrderEntity order) {
        return from(order, null, null, null, null);
    }

    public static PassengerOrderResponse from(RideOrderEntity order, PaymentEntity payment, String paymentToken) {
        return from(order, payment, paymentToken, null, null);
    }

    public static PassengerOrderResponse from(
            RideOrderEntity order,
            PaymentEntity payment,
            String paymentToken,
            DriverEntity driver,
            VehicleEntity vehicle) {
        return new PassengerOrderResponse(
                order.getOrderNo(), order.getSourceType(), order.getStatus(), order.getTripStage(),
                order.getCurrentDriverId(),
                driver == null ? null : driver.getName(),
                driver == null ? null : driver.getDriverNo(),
                vehicle == null ? null : vehicle.getPlateNo(),
                vehicle == null ? null : vehicle.getBrandModel(),
                order.getPickupAddress(), order.getPickupLatitude(), order.getPickupLongitude(),
                order.getDestinationAddress(), order.getDestinationLatitude(), order.getDestinationLongitude(),
                order.getPassengerCount(), order.getDepartureAt(), order.getRemark(), order.getFinalAmount(),
                order.getAcceptedAt(), order.getServiceStartedAt(), order.getArrivedDestinationAt(), order.getCreatedAt(),
                paymentToken, payment == null ? null : payment.getStatus());
    }
}
