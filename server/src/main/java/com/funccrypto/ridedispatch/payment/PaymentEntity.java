package com.funccrypto.ridedispatch.payment;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.funccrypto.ridedispatch.shared.error.BusinessException;

@Entity
@Table(name = "payment")
public class PaymentEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "payment_no", nullable = false, unique = true, length = 40) private String paymentNo;
    @Column(name = "order_id", nullable = false, unique = true) private Long orderId;
    @Column(nullable = false) private long amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private PaymentStatus status;
    @Column(name = "settlement_method", length = 30) private String settlementMethod;
    @Column(name = "access_token_hash", nullable = false, unique = true, length = 64) private String accessTokenHash;
    @Column(name = "access_token_created_at", nullable = false) private Instant accessTokenCreatedAt;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "settled_at") private Instant settledAt;
    @Version @Column(nullable = false) private long version;

    protected PaymentEntity() {}

    private PaymentEntity(String paymentNo, long amount, Long orderId, String accessTokenHash, Instant now,
            Instant expiresAt) {
        if (amount <= 0) throw new BusinessException("INVALID_PAYMENT_AMOUNT", "支付金额必须大于 0");
        if (paymentNo == null || paymentNo.isBlank()) throw new BusinessException("PAYMENT_NO_REQUIRED", "支付单号不能为空");
        if (orderId == null) throw new BusinessException("PAYMENT_ORDER_REQUIRED", "支付单必须绑定订单");
        this.paymentNo = paymentNo;
        this.amount = amount;
        this.orderId = orderId;
        this.status = PaymentStatus.PENDING;
        this.accessTokenHash = accessTokenHash;
        this.accessTokenCreatedAt = now;
        this.expiresAt = expiresAt;
        this.createdAt = now;
    }

    public static PaymentEntity pending(String paymentNo, long amount, Long orderId, String accessTokenHash, Instant now) {
        return new PaymentEntity(paymentNo, amount, orderId, accessTokenHash, now, null);
    }

    public static PaymentEntity pending(String paymentNo, long amount, Long orderId, String accessTokenHash,
            Instant now, Instant expiresAt) {
        return new PaymentEntity(paymentNo, amount, orderId, accessTokenHash, now, expiresAt);
    }

    public void assertAmountMatches(long callbackAmount) {
        if (callbackAmount != amount) {
            throw new BusinessException("PAYMENT_AMOUNT_MISMATCH", "支付回调金额与订单金额不一致");
        }
    }

    public void settle(PaymentChannel channel, String thirdPartyTransactionNo, Instant now) {
        if (status != PaymentStatus.PENDING) {
            throw new BusinessException("PAYMENT_ALREADY_SETTLED", "支付单已完成或不可支付");
        }
        if (channel == null || channel == PaymentChannel.OFFLINE) {
            throw new BusinessException("PAYMENT_CHANNEL_INVALID", "线上支付单渠道不合法");
        }
        if (thirdPartyTransactionNo == null || thirdPartyTransactionNo.isBlank()) {
            throw new BusinessException("PAYMENT_TRANSACTION_REQUIRED", "支付流水号不能为空");
        }
        this.status = PaymentStatus.PAID;
        this.settlementMethod = channel.name();
        this.settledAt = now;
    }

    public void settleOffline(Instant now) {
        if (status != PaymentStatus.PENDING) {
            throw new BusinessException("PAYMENT_ALREADY_SETTLED", "支付单已完成或不可支付");
        }
        this.status = PaymentStatus.PAID;
        this.settlementMethod = PaymentChannel.OFFLINE.name();
        this.settledAt = now;
    }

    public void expire(Instant now) {
        if (status == PaymentStatus.PENDING) {
            status = PaymentStatus.EXPIRED;
            settledAt = now;
        }
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public void replaceAccessTokenHash(String newHash, Instant now) {
        if (status != PaymentStatus.PENDING) {
            throw new BusinessException("PAYMENT_TOKEN_NOT_ROTATABLE", "支付单当前不能更新付款凭证");
        }
        if (newHash == null || newHash.isBlank()) {
            throw new BusinessException("PAYMENT_TOKEN_REQUIRED", "付款凭证不能为空");
        }
        this.accessTokenHash = newHash;
        this.accessTokenCreatedAt = now;
    }

    public Long getId() { return id; }
    public String getPaymentNo() { return paymentNo; }
    public Long getOrderId() { return orderId; }
    public long getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public String getSettlementMethod() { return settlementMethod; }
    public String getAccessTokenHash() { return accessTokenHash; }
    public Instant getAccessTokenCreatedAt() { return accessTokenCreatedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSettledAt() { return settledAt; }
}
