package com.funccrypto.ridedispatch.settlement;

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
@Table(name = "withdrawal")
public class WithdrawalEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "withdrawal_no", nullable = false, unique = true, length = 40) private String withdrawalNo;
    @Column(name = "driver_id", nullable = false) private Long driverId;
    @Column(name = "idempotency_key", unique = true, length = 120) private String idempotencyKey;
    @Column(nullable = false) private long amount;
    @Column(nullable = false, length = 30) private String channel;
    @Column(nullable = false, length = 255) private String account;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private WithdrawalStatus status;
    @Column(length = 500) private String reason;
    @Column(name = "reviewed_by") private Long reviewedBy;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "paid_by") private Long paidBy;
    @Column(name = "planned_paid_at") private Instant plannedPaidAt;
    @Column(name = "paid_at") private Instant paidAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected WithdrawalEntity() {}

    private WithdrawalEntity(String withdrawalNo, Long driverId, String idempotencyKey, long amount, String channel, String account, Instant now) {
        if (amount <= 0) throw new BusinessException("WITHDRAWAL_AMOUNT_INVALID", "提现金额必须大于 0");
        this.withdrawalNo = withdrawalNo;
        this.driverId = driverId;
        this.idempotencyKey = idempotencyKey;
        this.amount = amount;
        this.channel = channel;
        this.account = account;
        this.status = WithdrawalStatus.PENDING_REVIEW;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static WithdrawalEntity request(String withdrawalNo, Long driverId, long amount, String channel,
            String account, Instant now) {
        return new WithdrawalEntity(withdrawalNo, driverId, null, amount, channel, account, now);
    }

    public static WithdrawalEntity request(String withdrawalNo, Long driverId, String idempotencyKey, long amount,
            String channel, String account, Instant now) {
        return new WithdrawalEntity(withdrawalNo, driverId, idempotencyKey, amount, channel, account, now);
    }

    public void approve(Long operatorId, Instant now) {
        requireStatus(WithdrawalStatus.PENDING_REVIEW);
        status = WithdrawalStatus.APPROVED_PENDING_PAYMENT;
        reviewedBy = operatorId;
        reviewedAt = now;
        updatedAt = now;
    }

    public void reject(Long operatorId, String reason, Instant now) {
        requireStatus(WithdrawalStatus.PENDING_REVIEW);
        if (reason == null || reason.isBlank()) throw new BusinessException("WITHDRAWAL_REASON_REQUIRED", "驳回必须填写原因");
        status = WithdrawalStatus.REJECTED;
        this.reason = reason;
        reviewedBy = operatorId;
        reviewedAt = now;
        updatedAt = now;
    }

    public void markPaid(Long operatorId, Instant now) {
        requireStatus(WithdrawalStatus.APPROVED_PENDING_PAYMENT);
        status = WithdrawalStatus.PAID;
        paidBy = operatorId;
        paidAt = now;
        updatedAt = now;
    }

    private void requireStatus(WithdrawalStatus expected) {
        if (status != expected) throw new BusinessException("WITHDRAWAL_STATE_CONFLICT", "提现状态已变化，请刷新后重试");
    }

    public Long getId() { return id; }
    public String getWithdrawalNo() { return withdrawalNo; }
    public Long getDriverId() { return driverId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public long getAmount() { return amount; }
    public String getChannel() { return channel; }
    public String getAccount() { return account; }
    public WithdrawalStatus getStatus() { return status; }
    public String getReason() { return reason; }
    public Long getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public Long getPaidBy() { return paidBy; }
    public Instant getPlannedPaidAt() { return plannedPaidAt; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
