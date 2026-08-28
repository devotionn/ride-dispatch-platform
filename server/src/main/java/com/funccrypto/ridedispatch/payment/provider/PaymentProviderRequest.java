package com.funccrypto.ridedispatch.payment.provider;

import com.funccrypto.ridedispatch.shared.error.BusinessException;

public record PaymentProviderRequest(
        String paymentNo,
        String merchantOrderNo,
        long amountFen,
        String subject,
        String idempotencyKey) {

    public PaymentProviderRequest {
        if (paymentNo == null || paymentNo.isBlank()) {
            throw new BusinessException("PAYMENT_PROVIDER_PAYMENT_REQUIRED", "支付单号不能为空");
        }
        if (merchantOrderNo == null || merchantOrderNo.isBlank()) {
            throw new BusinessException("PAYMENT_PROVIDER_MERCHANT_ORDER_REQUIRED", "商户订单号不能为空");
        }
        if (amountFen <= 0) {
            throw new BusinessException("PAYMENT_PROVIDER_AMOUNT_INVALID", "支付金额必须大于 0");
        }
        if (subject == null || subject.isBlank()) {
            throw new BusinessException("PAYMENT_PROVIDER_SUBJECT_REQUIRED", "支付商品描述不能为空");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException("PAYMENT_PROVIDER_IDEMPOTENCY_REQUIRED", "支付幂等键不能为空");
        }
    }
}
