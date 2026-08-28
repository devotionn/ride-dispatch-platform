package com.funccrypto.ridedispatch.payment.reconciliation;

import com.funccrypto.ridedispatch.payment.PaymentStatus;
import com.funccrypto.ridedispatch.payment.provider.PaymentProviderStatus;

public class PaymentReconciliationComparator {

    public ReconciliationDiff compare(PaymentReconciliationSnapshot snapshot) {
        if (snapshot.localStatus() == null) {
            return diff(snapshot, ReconciliationStatus.PROVIDER_ONLY, "PROVIDER_ONLY");
        }
        if (snapshot.providerStatus() == null) {
            return diff(snapshot, ReconciliationStatus.LOCAL_ONLY, "LOCAL_ONLY");
        }
        if (!snapshot.localAmountFen().equals(snapshot.providerAmountFen())) {
            return diff(snapshot, ReconciliationStatus.AMOUNT_MISMATCH, "AMOUNT_MISMATCH");
        }
        if (snapshot.providerStatus() == PaymentProviderStatus.PAID
                && snapshot.localStatus() == PaymentStatus.PENDING) {
            return diff(snapshot, ReconciliationStatus.LOCAL_PENDING_PROVIDER_PAID,
                    "LOCAL_PENDING_PROVIDER_PAID");
        }
        if (snapshot.providerStatus() == PaymentProviderStatus.PAID
                && snapshot.localStatus() != PaymentStatus.PAID) {
            return diff(snapshot, ReconciliationStatus.STATUS_MISMATCH, "STATUS_MISMATCH");
        }
        if (snapshot.localStatus() == PaymentStatus.PAID
                && snapshot.providerStatus() != PaymentProviderStatus.PAID) {
            return diff(snapshot, ReconciliationStatus.STATUS_MISMATCH, "STATUS_MISMATCH");
        }
        if (snapshot.localStatus() == PaymentStatus.PAID
                && !snapshot.localTransactionNo().equals(snapshot.providerTransactionNo())) {
            return diff(snapshot, ReconciliationStatus.TRANSACTION_MISMATCH, "TRANSACTION_MISMATCH");
        }
        if (!providerStatusMatchesLocal(snapshot)) {
            return diff(snapshot, ReconciliationStatus.STATUS_MISMATCH, "STATUS_MISMATCH");
        }
        return diff(snapshot, ReconciliationStatus.MATCHED, "MATCHED");
    }

    private boolean providerStatusMatchesLocal(PaymentReconciliationSnapshot snapshot) {
        return switch (snapshot.localStatus()) {
            case PENDING -> snapshot.providerStatus() == PaymentProviderStatus.PENDING
                    || snapshot.providerStatus() == PaymentProviderStatus.CREATED;
            case PAID -> snapshot.providerStatus() == PaymentProviderStatus.PAID;
            case EXPIRED -> snapshot.providerStatus() == PaymentProviderStatus.EXPIRED;
            case CANCELLED -> snapshot.providerStatus() == PaymentProviderStatus.FAILED;
        };
    }

    private ReconciliationDiff diff(PaymentReconciliationSnapshot snapshot, ReconciliationStatus status,
            String reasonCode) {
        return new ReconciliationDiff(snapshot.merchantOrderNo(), status, reasonCode);
    }
}
