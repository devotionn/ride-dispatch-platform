package com.funccrypto.ridedispatch.order;

public enum OrderStatus {
    PENDING_DISPATCH,
    PENDING_DRIVER_CONFIRM,
    ACCEPTED,
    IN_SERVICE,
    PENDING_PAYMENT,
    COMPLETED,
    CANCELLED,
    EXCEPTION
}
