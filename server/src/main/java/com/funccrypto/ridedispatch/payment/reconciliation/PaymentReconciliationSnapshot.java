package com.funccrypto.ridedispatch.payment.reconciliation;

import com.funccrypto.ridedispatch.payment.PaymentAttemptStatus;
import com.funccrypto.ridedispatch.payment.PaymentStatus;
import com.funccrypto.ridedispatch.payment.provider.PaymentProviderStatus;
import com.funccrypto.ridedispatch.shared.error.BusinessException;

public record PaymentReconciliationSnapshot(
        String merchantOrderNo,
        Long localAmountFen,
        PaymentStatus localStatus,
        PaymentAttemptStatus localAttemptStatus,
        String localTransactionNo,
        PaymentProviderStatus providerStatus,
        String providerTransactionNo,
        Long providerAmountFen) {

    public PaymentReconciliationSnapshot {
        if (merchantOrderNo == null || merchantOrderNo.isBlank()) {
            throw new BusinessException("RECONCILIATION_MERCHANT_ORDER_REQUIRED", "对账商户订单号不能为空");
        }
        validateLocal(localAmountFen, localStatus, localAttemptStatus, localTransactionNo);
        validateProvider(providerStatus, providerTransactionNo, providerAmountFen);
    }

    private static void validateLocal(Long amount, PaymentStatus status, PaymentAttemptStatus attempt,
            String transaction) {
        boolean present = amount != null || status != null || attempt != null || transaction != null;
        if (!present) return;
        if (amount == null || amount <= 0 || status == null || attempt == null) {
            throw new BusinessException("RECONCILIATION_LOCAL_SNAPSHOT_INVALID", "本地对账快照不完整");
        }
        if (status == PaymentStatus.PAID && (transaction == null || transaction.isBlank())) {
            throw new BusinessException("RECONCILIATION_LOCAL_TRANSACTION_REQUIRED", "本地已支付记录缺少流水号");
        }
    }

    private static void validateProvider(PaymentProviderStatus status, String transaction, Long amount) {
        boolean present = status != null || transaction != null || amount != null;
        if (!present) return;
        if (status == null || amount == null || amount <= 0) {
            throw new BusinessException("RECONCILIATION_PROVIDER_SNAPSHOT_INVALID", "第三方对账快照不完整");
        }
        if (status == PaymentProviderStatus.PAID && (transaction == null || transaction.isBlank())) {
            throw new BusinessException("RECONCILIATION_PROVIDER_TRANSACTION_REQUIRED", "第三方已支付记录缺少流水号");
        }
    }
}
