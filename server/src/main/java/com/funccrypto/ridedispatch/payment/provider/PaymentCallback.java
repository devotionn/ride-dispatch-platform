package com.funccrypto.ridedispatch.payment.provider;

import com.funccrypto.ridedispatch.shared.error.BusinessException;

public record PaymentCallback(
        String merchantOrderNo,
        String providerTransactionNo,
        long amountFen,
        String rawPayload,
        String signature) {

    public PaymentCallback {
        if (merchantOrderNo == null || merchantOrderNo.isBlank()) {
            throw new BusinessException("PAYMENT_CALLBACK_MERCHANT_ORDER_REQUIRED", "回调商户订单号不能为空");
        }
        if (providerTransactionNo == null || providerTransactionNo.isBlank()) {
            throw new BusinessException("PAYMENT_CALLBACK_TRANSACTION_REQUIRED", "第三方支付流水号不能为空");
        }
        if (amountFen <= 0) {
            throw new BusinessException("PAYMENT_CALLBACK_AMOUNT_INVALID", "回调金额必须大于 0");
        }
        if (rawPayload == null || rawPayload.isBlank()) {
            throw new BusinessException("PAYMENT_CALLBACK_PAYLOAD_REQUIRED", "回调原文不能为空");
        }
        if (signature == null || signature.isBlank()) {
            throw new BusinessException("PAYMENT_CALLBACK_SIGNATURE_REQUIRED", "回调签名不能为空");
        }
    }
}
