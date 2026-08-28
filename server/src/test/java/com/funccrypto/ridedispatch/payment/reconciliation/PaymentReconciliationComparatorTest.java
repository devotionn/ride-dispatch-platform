package com.funccrypto.ridedispatch.payment.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import com.funccrypto.ridedispatch.payment.PaymentAttemptStatus;
import com.funccrypto.ridedispatch.payment.PaymentStatus;
import com.funccrypto.ridedispatch.payment.provider.PaymentProviderStatus;
import org.junit.jupiter.api.Test;

class PaymentReconciliationComparatorTest {

    private final PaymentReconciliationComparator comparator = new PaymentReconciliationComparator();

    @Test
    void exactPaidSnapshotIsMatched() {
        var diff = comparator.compare(snapshot(PaymentStatus.PAID, PaymentAttemptStatus.SUCCEEDED,
                "TX-1", PaymentProviderStatus.PAID, "TX-1", 1200L));

        assertThat(diff.status()).isEqualTo(ReconciliationStatus.MATCHED);
        assertThat(diff.reasonCode()).isEqualTo("MATCHED");
    }

    @Test
    void localPendingProviderPaidIsActionable() {
        var diff = comparator.compare(snapshot(PaymentStatus.PENDING, PaymentAttemptStatus.PROCESSING,
                null, PaymentProviderStatus.PAID, "TX-1", 1200L));

        assertThat(diff.status()).isEqualTo(ReconciliationStatus.LOCAL_PENDING_PROVIDER_PAID);
    }

    @Test
    void amountMismatchWinsOverOtherDifferences() {
        var diff = comparator.compare(snapshot(PaymentStatus.PAID, PaymentAttemptStatus.SUCCEEDED,
                "TX-1", PaymentProviderStatus.PAID, "TX-1", 1300L));

        assertThat(diff.status()).isEqualTo(ReconciliationStatus.AMOUNT_MISMATCH);
    }

    @Test
    void transactionMismatchIsReportedForPaidRecords() {
        var diff = comparator.compare(snapshot(PaymentStatus.PAID, PaymentAttemptStatus.SUCCEEDED,
                "TX-1", PaymentProviderStatus.PAID, "TX-2", 1200L));

        assertThat(diff.status()).isEqualTo(ReconciliationStatus.TRANSACTION_MISMATCH);
    }

    @Test
    void providerOnlyAndLocalOnlySnapshotsAreDistinguished() {
        assertThat(comparator.compare(new PaymentReconciliationSnapshot(
                "MCH-1", 1200L, PaymentStatus.PENDING, PaymentAttemptStatus.PROCESSING, null,
                PaymentProviderStatus.PAID, "TX-1", 1200L)).status())
                .isEqualTo(ReconciliationStatus.LOCAL_PENDING_PROVIDER_PAID);
        assertThat(comparator.compare(new PaymentReconciliationSnapshot(
                "MCH-1", null, null, null, null,
                PaymentProviderStatus.PAID, "TX-1", 1200L)).status())
                .isEqualTo(ReconciliationStatus.PROVIDER_ONLY);
        assertThat(comparator.compare(new PaymentReconciliationSnapshot(
                "MCH-1", 1200L, PaymentStatus.PENDING, PaymentAttemptStatus.PROCESSING, null,
                null, null, null)).status())
                .isEqualTo(ReconciliationStatus.LOCAL_ONLY);
    }

    private PaymentReconciliationSnapshot snapshot(PaymentStatus localStatus, PaymentAttemptStatus attemptStatus,
            String localTransaction, PaymentProviderStatus providerStatus, String providerTransaction, Long providerAmount) {
        return new PaymentReconciliationSnapshot("MCH-1", 1200L, localStatus, attemptStatus, localTransaction,
                providerStatus, providerTransaction, providerAmount);
    }
}
