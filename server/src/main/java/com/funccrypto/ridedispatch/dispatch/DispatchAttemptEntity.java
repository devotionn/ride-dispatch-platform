package com.funccrypto.ridedispatch.dispatch;

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
@Table(name = "dispatch_attempt")
public class DispatchAttemptEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_id", nullable = false)
    private Long orderId;
    @Column(name = "target_driver_id", nullable = false)
    private Long targetDriverId;
    @Enumerated(EnumType.STRING)
    @Column(name = "dispatch_type", nullable = false, length = 40)
    private DispatchType dispatchType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private DispatchAttemptStatus status;
    @Column(name = "dispatched_by")
    private Long dispatchedBy;
    @Column(name = "dispatched_at", nullable = false)
    private Instant dispatchedAt;
    @Column(name = "responded_at")
    private Instant respondedAt;
    @Column(name = "reject_reason_code", length = 60)
    private String rejectReasonCode;
    @Column(name = "reject_reason_text", length = 255)
    private String rejectReasonText;
    @Column(name = "reassign_from_driver_id")
    private Long reassignFromDriverId;
    @Column(name = "reassign_reason", length = 255)
    private String reassignReason;
    @Column(name = "invalidated_at")
    private Instant invalidatedAt;
    @Version
    @Column(nullable = false)
    private long version;

    protected DispatchAttemptEntity() {
    }

    public DispatchAttemptEntity(Long orderId, Long targetDriverId, DispatchType dispatchType, Long dispatchedBy, Instant dispatchedAt) {
        this.orderId = orderId;
        this.targetDriverId = targetDriverId;
        this.dispatchType = dispatchType;
        this.dispatchedBy = dispatchedBy;
        this.dispatchedAt = dispatchedAt;
        this.status = DispatchAttemptStatus.WAITING;
    }

    public void accept(Long actingDriverId, Instant now) {
        requireWaiting();
        requireTargetDriver(actingDriverId);
        this.status = DispatchAttemptStatus.ACCEPTED;
        this.respondedAt = now;
    }

    public void reject(Long actingDriverId, String reasonCode, String reasonText, Instant now) {
        requireWaiting();
        requireTargetDriver(actingDriverId);
        if ((reasonCode == null || reasonCode.isBlank()) && (reasonText == null || reasonText.isBlank())) {
            throw new BusinessException("REJECT_REASON_REQUIRED", "拒绝订单必须填写原因");
        }
        this.status = DispatchAttemptStatus.REJECTED;
        this.rejectReasonCode = reasonCode;
        this.rejectReasonText = reasonText;
        this.respondedAt = now;
    }

    public void invalidateByOrder(Instant now) {
        requireWaiting();
        this.status = DispatchAttemptStatus.CANCELLED_BY_ORDER;
        this.invalidatedAt = now;
    }

    private void requireTargetDriver(Long actingDriverId) {
        if (!targetDriverId.equals(actingDriverId)) {
            throw new BusinessException("DISPATCH_ATTEMPT_NOT_TARGET_DRIVER", "该派单不属于当前司机");
        }
    }

    private void requireWaiting() {
        if (status != DispatchAttemptStatus.WAITING) {
            throw new BusinessException("DISPATCH_ATTEMPT_EXPIRED", "该派单已失效");
        }
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public Long getTargetDriverId() { return targetDriverId; }
    public DispatchType getDispatchType() { return dispatchType; }
    public DispatchAttemptStatus getStatus() { return status; }
    public Long getDispatchedBy() { return dispatchedBy; }
    public Instant getDispatchedAt() { return dispatchedAt; }
    public Instant getRespondedAt() { return respondedAt; }
    public String getRejectReasonCode() { return rejectReasonCode; }
    public String getRejectReasonText() { return rejectReasonText; }
}
