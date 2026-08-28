package com.funccrypto.ridedispatch.payment;

import java.time.Instant;

import com.funccrypto.ridedispatch.shared.error.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "payment_exception")
public class PaymentExceptionEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "exception_no", nullable = false, unique = true, length = 40) private String exceptionNo;
    @Column(name = "idempotency_key", unique = true, length = 80) private String idempotencyKey;
    @Column(name = "payment_id", nullable = false) private Long paymentId;
    @Column(name = "order_id", nullable = false) private Long orderId;
    @Column(name = "requested_amount", nullable = false) private long requestedAmount;
    @Column(nullable = false, length = 500) private String reason;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private PaymentExceptionStatus status;
    @Column(name = "external_refund_ref", length = 120) private String externalRefundRef;
    @Column(name = "resolution_note", length = 500) private String resolutionNote;
    @Column(name = "created_by", nullable = false) private Long createdBy;
    @Column(name = "resolved_by") private Long resolvedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "resolved_at") private Instant resolvedAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected PaymentExceptionEntity() {}

    private PaymentExceptionEntity(String exceptionNo, String idempotencyKey, Long paymentId, Long orderId,
            long requestedAmount, String reason, Long createdBy, Instant now) {
        if (requestedAmount <= 0) throw new BusinessException("PAYMENT_EXCEPTION_AMOUNT_INVALID", "退款异常金额必须大于 0");
        if (reason == null || reason.isBlank()) throw new BusinessException("PAYMENT_EXCEPTION_REASON_REQUIRED", "退款异常必须填写原因");
        if (idempotencyKey == null || idempotencyKey.isBlank()) throw new BusinessException("PAYMENT_EXCEPTION_IDEMPOTENCY_KEY_REQUIRED", "退款异常幂等键不能为空");
        this.exceptionNo = exceptionNo;
        this.idempotencyKey = idempotencyKey.trim();
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.requestedAmount = requestedAmount;
        this.reason = reason.trim();
        this.status = PaymentExceptionStatus.OPEN;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static PaymentExceptionEntity open(String exceptionNo, String idempotencyKey, Long paymentId, Long orderId,
            long requestedAmount, String reason, Long createdBy, Instant now) {
        return new PaymentExceptionEntity(exceptionNo, idempotencyKey, paymentId, orderId, requestedAmount, reason, createdBy, now);
    }

    public void resolve(Long operatorId, String externalRef, String note, Instant now) {
        requireOpen();
        if (externalRef == null || externalRef.isBlank()) {
            throw new BusinessException("PAYMENT_EXCEPTION_EXTERNAL_REF_REQUIRED", "解决退款异常必须填写外部退款凭证");
        }
        if (note == null || note.isBlank()) {
            throw new BusinessException("PAYMENT_EXCEPTION_NOTE_REQUIRED", "解决退款异常必须填写处理备注");
        }
        status = PaymentExceptionStatus.RESOLVED;
        externalRefundRef = externalRef.trim();
        resolutionNote = note.trim();
        resolvedBy = operatorId;
        resolvedAt = now;
        updatedAt = now;
    }

    public void reject(Long operatorId, String note, Instant now) {
        requireOpen();
        if (note == null || note.isBlank()) {
            throw new BusinessException("PAYMENT_EXCEPTION_NOTE_REQUIRED", "驳回退款异常必须填写处理备注");
        }
        status = PaymentExceptionStatus.REJECTED;
        resolutionNote = note.trim();
        resolvedBy = operatorId;
        resolvedAt = now;
        updatedAt = now;
    }

    private void requireOpen() {
        if (status != PaymentExceptionStatus.OPEN) {
            throw new BusinessException("PAYMENT_EXCEPTION_STATE_CONFLICT", "退款异常已处理，请刷新后重试");
        }
    }

    public Long getId() { return id; }
    public String getExceptionNo() { return exceptionNo; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Long getPaymentId() { return paymentId; }
    public Long getOrderId() { return orderId; }
    public long getRequestedAmount() { return requestedAmount; }
    public String getReason() { return reason; }
    public PaymentExceptionStatus getStatus() { return status; }
    public String getExternalRefundRef() { return externalRefundRef; }
    public String getResolutionNote() { return resolutionNote; }
    public Long getCreatedBy() { return createdBy; }
    public Long getResolvedBy() { return resolvedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
