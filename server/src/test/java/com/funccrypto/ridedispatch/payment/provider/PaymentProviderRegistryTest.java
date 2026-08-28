package com.funccrypto.ridedispatch.payment.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.funccrypto.ridedispatch.payment.PaymentChannel;
import com.funccrypto.ridedispatch.shared.error.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentProviderRegistryTest {

    @Test
    void emptyRegistryKeepsProductionMockProviderUnavailable() {
        var registry = new PaymentProviderRegistry(List.of());

        assertThat(registry.providers()).isEmpty();
        assertThatThrownBy(() -> registry.require(PaymentChannel.MOCK_ALIPAY))
                .isInstanceOf(BusinessException.class)
                .hasMessage("支付渠道暂未配置");
    }

    @Test
    void duplicateChannelsAreRejected() {
        PaymentProvider first = provider(PaymentChannel.MOCK_ALIPAY);
        PaymentProvider second = provider(PaymentChannel.MOCK_ALIPAY);

        assertThatThrownBy(() -> new PaymentProviderRegistry(List.of(first, second)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("支付渠道 Provider 重复配置");
    }

    @Test
    void configuredProviderCanBeResolvedWithoutPersistence() {
        PaymentProvider provider = provider(PaymentChannel.MOCK_WECHAT);
        var registry = new PaymentProviderRegistry(List.of(provider));

        assertThat(registry.require(PaymentChannel.MOCK_WECHAT)).isSameAs(provider);
    }

    private PaymentProvider provider(PaymentChannel channel) {
        return new PaymentProvider() {
            @Override public PaymentChannel channel() { return channel; }
            @Override public PaymentProviderResult create(PaymentProviderRequest request) { return null; }
            @Override public PaymentCallback verifyCallback(String rawPayload, String signature) { return null; }
            @Override public PaymentQueryResult query(String merchantOrderNo) { return null; }
        };
    }
}
