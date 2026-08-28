package com.funccrypto.ridedispatch.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.junit.jupiter.api.Test;

/**
 * Payment contract tests are intentionally written before the implementation.
 * They pin the local rules: integer fen, one successful settlement and callback
 * amount validation.
 */
class PaymentServiceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-24T00:00:00Z");

    @Test
    void paymentLocksAmountAndAllowsOnlyOneSuccessfulSettlement() {
        PaymentEntity payment = PaymentEntity.pending(
                "PAYLOCAL00000000000001", 120_000L, 101L, "token-hash", NOW);

        payment.settle(PaymentChannel.MOCK_WECHAT, "mock-tx-1", NOW.plusSeconds(1));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getAmount()).isEqualTo(120_000L);
        assertThat(payment.getSettlementMethod()).isEqualTo(PaymentChannel.MOCK_WECHAT.name());
        assertThatThrownBy(() -> payment.settle(PaymentChannel.MOCK_ALIPAY, "mock-tx-2", NOW.plusSeconds(2)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("支付");
    }

    @Test
    void callbackAmountMustMatchLockedPaymentAmount() {
        PaymentEntity payment = PaymentEntity.pending(
                "PAYLOCAL00000000000002", 120_000L, 102L, "token-hash-2", NOW);

        assertThatThrownBy(() -> payment.assertAmountMatches(120_001L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("金额");
    }
}
