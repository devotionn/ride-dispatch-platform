package com.funccrypto.ridedispatch.dispatch;

public enum DispatchAttemptStatus {
    WAITING,
    ACCEPTED,
    REJECTED,
    CANCELLED_BY_REASSIGN,
    CANCELLED_BY_ORDER
}
