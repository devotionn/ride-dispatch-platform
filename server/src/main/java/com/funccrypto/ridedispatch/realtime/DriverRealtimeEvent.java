package com.funccrypto.ridedispatch.realtime;

import java.time.Instant;

import com.funccrypto.ridedispatch.order.OrderStatus;

public record DriverRealtimeEvent(
        String eventType,
        Long driverId,
        Long attemptId,
        String orderNo,
        OrderStatus orderStatus,
        Instant occurredAt) {
}
