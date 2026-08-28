package com.funccrypto.ridedispatch.payment.provider;

import com.funccrypto.ridedispatch.payment.PaymentChannel;
import com.funccrypto.ridedispatch.shared.error.BusinessException;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Keeps provider availability explicit. An empty registry is valid in production
 * until real merchant credentials are configured; callers receive a clear boundary error.
 */
@Component
public class PaymentProviderRegistry {

    private final Map<PaymentChannel, PaymentProvider> providers;

    public PaymentProviderRegistry(Collection<PaymentProvider> providers) {
        EnumMap<PaymentChannel, PaymentProvider> index = new EnumMap<>(PaymentChannel.class);
        Collection<PaymentProvider> configuredProviders = providers == null ? List.<PaymentProvider>of() : providers;
        for (PaymentProvider provider : configuredProviders) {
            if (provider == null || provider.channel() == null) {
                throw new BusinessException("PAYMENT_PROVIDER_CHANNEL_REQUIRED", "支付渠道不能为空");
            }
            if (index.putIfAbsent(provider.channel(), provider) != null) {
                throw new BusinessException("PAYMENT_PROVIDER_DUPLICATE", "支付渠道 Provider 重复配置");
            }
        }
        this.providers = Collections.unmodifiableMap(index);
    }

    public PaymentProvider require(PaymentChannel channel) {
        PaymentProvider provider = providers.get(channel);
        if (provider == null) {
            throw new BusinessException("PAYMENT_PROVIDER_UNAVAILABLE", "支付渠道暂未配置");
        }
        return provider;
    }

    public List<PaymentProvider> providers() {
        return List.copyOf(providers.values());
    }
}
