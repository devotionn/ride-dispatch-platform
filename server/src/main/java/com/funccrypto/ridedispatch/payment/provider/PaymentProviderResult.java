package com.funccrypto.ridedispatch.payment.provider;

import com.funccrypto.ridedispatch.payment.PaymentChannel;
import com.funccrypto.ridedispatch.shared.error.BusinessException;

public record PaymentProviderResult(
        PaymentChannel channel,
        String merchantOrderNo,
        PaymentProviderStatus status,
        String providerTransactionNo,
        String checkoutPayload) {

    public PaymentProviderResult {
        if (channel == null) {
            throw new BusinessException("PAYMENT_PROVIDER_CHANNEL_REQUIRED", "支付渠道不能为空");
        }
        if (merchantOrderNo == null || merchantOrderNo.isBlank()) {
            throw new BusinessException("PAYMENT_PROVIDER_MERCHANT_ORDER_REQUIRED", "商户订单号不能为空");
        }
        if (status == null) {
            throw new BusinessException("PAYMENT_PROVIDER_STATUS_REQUIRED", "支付渠道状态不能为空");
        }
        if (status == PaymentProviderStatus.PAID
                && (providerTransactionNo == null || providerTransactionNo.isBlank())) {
            throw new BusinessException("PAYMENT_PROVIDER_TRANSACTION_REQUIRED", "第三方支付流水号不能为空");
        }
    }
}
