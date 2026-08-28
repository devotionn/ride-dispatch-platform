package com.funccrypto.ridedispatch.payment.reconciliation;

import com.funccrypto.ridedispatch.shared.error.BusinessException;

public record ReconciliationDiff(String merchantOrderNo, ReconciliationStatus status, String reasonCode) {

    public ReconciliationDiff {
        if (merchantOrderNo == null || merchantOrderNo.isBlank()) {
            throw new BusinessException("RECONCILIATION_MERCHANT_ORDER_REQUIRED", "对账商户订单号不能为空");
        }
        if (status == null || reasonCode == null || reasonCode.isBlank()) {
            throw new BusinessException("RECONCILIATION_RESULT_INVALID", "对账结果不完整");
        }
    }
}
