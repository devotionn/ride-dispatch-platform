package com.funccrypto.ridedispatch.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.funccrypto.ridedispatch.payment.PaymentChannel;
import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.junit.jupiter.api.Test;

class PaymentProviderContractTest {

    @Test
    void paymentRequestRequiresPositiveFenAmountAndIdentifiers() {
        assertThatThrownBy(() -> new PaymentProviderRequest("", "MCH-1", 1200, "车费", "key-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("支付单号不能为空");
        assertThatThrownBy(() -> new PaymentProviderRequest("PAY-1", "MCH-1", 0, "车费", "key-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("支付金额必须大于 0");
        assertThatThrownBy(() -> new PaymentProviderRequest("PAY-1", "", 1200, "车费", "key-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("商户订单号不能为空");
    }

    @Test
    void callbackRequiresTransactionAndExactAmount() {
        assertThatThrownBy(() -> new PaymentCallback("MCH-1", "", 1200, "payload", "signature"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("第三方支付流水号不能为空");
        assertThatThrownBy(() -> new PaymentCallback("MCH-1", "TX-1", 0, "payload", "signature"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("回调金额必须大于 0");
    }

    @Test
    void providerResultAndQueryResultCarryChannelAndMerchantOrder() {
        var result = new PaymentProviderResult(
                PaymentChannel.MOCK_ALIPAY, "MCH-1", PaymentProviderStatus.PENDING, null, "checkout");
        var query = new PaymentQueryResult(
                PaymentChannel.MOCK_ALIPAY, "MCH-1", PaymentProviderStatus.PAID, "TX-1", 1200L);

        assertThat(result.channel()).isEqualTo(PaymentChannel.MOCK_ALIPAY);
        assertThat(result.merchantOrderNo()).isEqualTo("MCH-1");
        assertThat(query.providerTransactionNo()).isEqualTo("TX-1");
        assertThat(query.amountFen()).isEqualTo(1200L);
    }
}
