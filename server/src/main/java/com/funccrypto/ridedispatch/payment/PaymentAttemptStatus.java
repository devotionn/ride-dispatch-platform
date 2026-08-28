package com.funccrypto.ridedispatch.payment;

public enum PaymentAttemptStatus {
    CREATED,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    IGNORED_ALREADY_SETTLED
}
