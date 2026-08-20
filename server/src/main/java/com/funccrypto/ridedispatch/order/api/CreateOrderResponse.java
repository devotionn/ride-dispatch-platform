package com.funccrypto.ridedispatch.order.api;

import com.funccrypto.ridedispatch.order.OrderStatus;

public record CreateOrderResponse(String orderNo, OrderStatus status, String passengerAccessToken) {
}
