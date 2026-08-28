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
@Table(name = "payment_attempt")
public class PaymentAttemptEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "attempt_no", nullable = false, unique = true, length = 40) private String attemptNo;
    @Column(name = "payment_id", nullable = false) private Long paymentId;
    @Column(name = "idempotency_key", unique = true, length = 120) private String idempotencyKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private PaymentChannel channel;
    @Column(name = "merchant_order_no", nullable = false, unique = true, length = 80) private String merchantOrderNo;
    @Column(name = "third_party_transaction_no", unique = true, length = 100) private String thirdPartyTransactionNo;
    @Column(nullable = false) private long amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private PaymentAttemptStatus status;
    @Column(name = "callback_payload_digest", length = 64) private String callbackPayloadDigest;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "paid_at") private Instant paidAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected PaymentAttemptEntity() {}

    private PaymentAttemptEntity(String attemptNo, Long paymentId, String idempotencyKey, PaymentChannel channel,
            String merchantOrderNo, long amount, Instant now) {
        if (paymentId == null) throw new BusinessException("PAYMENT_REQUIRED", "支付尝试必须绑定支付单");
        if (amount <= 0) throw new BusinessException("INVALID_PAYMENT_AMOUNT", "支付金额必须大于 0");
        this.attemptNo = attemptNo;
        this.paymentId = paymentId;
        this.idempotencyKey = idempotencyKey;
        this.channel = channel;
        this.merchantOrderNo = merchantOrderNo;
        this.amount = amount;
        this.status = PaymentAttemptStatus.CREATED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static PaymentAttemptEntity create(String attemptNo, Long paymentId, PaymentChannel channel,
            String merchantOrderNo, long amount, Instant now) {
        return new PaymentAttemptEntity(attemptNo, paymentId, null, channel, merchantOrderNo, amount, now);
    }

    public static PaymentAttemptEntity create(String attemptNo, Long paymentId, String idempotencyKey,
            PaymentChannel channel, String merchantOrderNo, long amount, Instant now) {
        return new PaymentAttemptEntity(attemptNo, paymentId, idempotencyKey, channel, merchantOrderNo, amount, now);
    }

    public void markProcessing(Instant now) {
        if (status != PaymentAttemptStatus.CREATED) {
            throw new BusinessException("PAYMENT_ATTEMPT_STATE_CONFLICT", "支付尝试状态不可开始");
        }
        status = PaymentAttemptStatus.PROCESSING;
        updatedAt = now;
    }

    public void markSucceeded(String thirdPartyTransactionNo, String callbackPayloadDigest, Instant now) {
        if (status == PaymentAttemptStatus.SUCCEEDED) return;
        if (status == PaymentAttemptStatus.IGNORED_ALREADY_SETTLED) return;
        if (status != PaymentAttemptStatus.CREATED && status != PaymentAttemptStatus.PROCESSING) {
            throw new BusinessException("PAYMENT_ATTEMPT_STATE_CONFLICT", "支付尝试状态不可成功");
        }
        if (thirdPartyTransactionNo == null || thirdPartyTransactionNo.isBlank()) {
            throw new BusinessException("PAYMENT_TRANSACTION_REQUIRED", "支付流水号不能为空");
        }
        status = PaymentAttemptStatus.SUCCEEDED;
        this.thirdPartyTransactionNo = thirdPartyTransactionNo;
        this.callbackPayloadDigest = callbackPayloadDigest;
        paidAt = now;
        updatedAt = now;
    }

    public void markFailed(String callbackPayloadDigest, Instant now) {
        if (status == PaymentAttemptStatus.SUCCEEDED) {
            throw new BusinessException("PAYMENT_ATTEMPT_ALREADY_SUCCEEDED", "成功的支付尝试不能标记失败");
        }
        status = PaymentAttemptStatus.FAILED;
        this.callbackPayloadDigest = callbackPayloadDigest;
        updatedAt = now;
    }

    public void markIgnoredAlreadySettled(Instant now) {
        status = PaymentAttemptStatus.IGNORED_ALREADY_SETTLED;
        updatedAt = now;
    }

    public Long getId() { return id; }
    public String getAttemptNo() { return attemptNo; }
    public Long getPaymentId() { return paymentId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public PaymentChannel getChannel() { return channel; }
    public String getMerchantOrderNo() { return merchantOrderNo; }
    public String getThirdPartyTransactionNo() { return thirdPartyTransactionNo; }
    public long getAmount() { return amount; }
    public PaymentAttemptStatus getStatus() { return status; }
    public String getCallbackPayloadDigest() { return callbackPayloadDigest; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
