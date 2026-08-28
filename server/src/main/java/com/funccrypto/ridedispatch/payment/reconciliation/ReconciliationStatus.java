package com.funccrypto.ridedispatch.payment.reconciliation;

public enum ReconciliationStatus {
    MATCHED,
    LOCAL_PENDING_PROVIDER_PAID,
    AMOUNT_MISMATCH,
    TRANSACTION_MISMATCH,
    STATUS_MISMATCH,
    PROVIDER_ONLY,
    LOCAL_ONLY
}
