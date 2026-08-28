package com.funccrypto.ridedispatch.settlement;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.funccrypto.ridedispatch.shared.error.BusinessException;

@Entity
@Table(name = "driver_account")
public class DriverAccountEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "driver_id", nullable = false, unique = true) private Long driverId;
    @Column(name = "available_balance", nullable = false) private long availableBalance;
    @Column(name = "frozen_balance", nullable = false) private long frozenBalance;
    @Column(name = "business_income_total", nullable = false) private long businessIncome;
    @Version @Column(nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected DriverAccountEntity() {}

    private DriverAccountEntity(Long driverId, Instant now) {
        if (driverId == null) throw new BusinessException("DRIVER_REQUIRED", "司机不能为空");
        this.driverId = driverId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static DriverAccountEntity empty(Long driverId, Instant now) {
        return new DriverAccountEntity(driverId, now);
    }

    public void applyIncome(String eventKey, long businessIncomeAmount, long withdrawableDelta, Instant now) {
        if (businessIncomeAmount < 0 || withdrawableDelta < 0) {
            throw new BusinessException("LEDGER_AMOUNT_INVALID", "收入金额不能为负数");
        }
        long nextAvailable = Math.addExact(availableBalance, withdrawableDelta);
        businessIncome = Math.addExact(businessIncome, businessIncomeAmount);
        availableBalance = nextAvailable;
        updatedAt = now;
    }

    public void applyBusinessAdjustment(long delta, Instant now) {
        if (delta == 0) throw new BusinessException("LEDGER_AMOUNT_INVALID", "纠偏金额不能为 0");
        long nextIncome = Math.addExact(businessIncome, delta);
        if (nextIncome < 0) throw new BusinessException("BUSINESS_INCOME_NEGATIVE", "纠偏后累计营收不能为负数");
        businessIncome = nextIncome;
        updatedAt = now;
    }

    public void reserveWithdrawal(String withdrawalNo, long amount, Instant now) {
        if (amount <= 0) throw new BusinessException("WITHDRAWAL_AMOUNT_INVALID", "提现金额必须大于 0");
        if (amount > availableBalance) throw new BusinessException("INSUFFICIENT_AVAILABLE_BALANCE", "可提现余额不足");
        availableBalance -= amount;
        frozenBalance = Math.addExact(frozenBalance, amount);
        updatedAt = now;
    }

    public void releaseFrozen(long amount, Instant now) {
        if (amount <= 0 || amount > frozenBalance) {
            throw new BusinessException("FROZEN_BALANCE_INVALID", "冻结余额不足");
        }
        frozenBalance -= amount;
        availableBalance = Math.addExact(availableBalance, amount);
        updatedAt = now;
    }

    public void consumeFrozen(long amount, Instant now) {
        if (amount <= 0 || amount > frozenBalance) {
            throw new BusinessException("FROZEN_BALANCE_INVALID", "冻结余额不足");
        }
        frozenBalance -= amount;
        updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getDriverId() { return driverId; }
    public long getAvailableBalance() { return availableBalance; }
    public long getFrozenBalance() { return frozenBalance; }
    public long getBusinessIncome() { return businessIncome; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
