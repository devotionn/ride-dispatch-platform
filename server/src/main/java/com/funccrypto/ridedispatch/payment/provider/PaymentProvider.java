package com.funccrypto.ridedispatch.payment.provider;

import com.funccrypto.ridedispatch.payment.PaymentChannel;

/**
 * Protocol boundary for a real payment channel. Implementations must not mutate
 * Payment, Order, or Ledger entities; settlement is handled by the application service
 * after callback verification and amount/idempotency checks.
 */
public interface PaymentProvider {

    PaymentChannel channel();

    PaymentProviderResult create(PaymentProviderRequest request);

    PaymentCallback verifyCallback(String rawPayload, String signature);

    PaymentQueryResult query(String merchantOrderNo);
}
