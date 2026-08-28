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

@Entity
@Table(name = "driver_ledger")
public class DriverLedgerEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "ledger_no", nullable = false, unique = true, length = 40) private String ledgerNo;
    @Column(name = "driver_id", nullable = false) private Long driverId;
    @Column(name = "order_id") private Long orderId;
    @Column(name = "withdrawal_id") private Long withdrawalId;
    @Enumerated(EnumType.STRING) @Column(name = "ledger_type", nullable = false, length = 40) private LedgerType ledgerType;
    @Column(nullable = false) private long amount;
    @Column(name = "available_before", nullable = false) private long availableBefore;
    @Column(name = "available_after", nullable = false) private long availableAfter;
    @Column(name = "frozen_before", nullable = false) private long frozenBefore;
    @Column(name = "frozen_after", nullable = false) private long frozenAfter;
    @Column(name = "business_income_amount", nullable = false) private long businessIncomeAmount;
    @Column(name = "withdrawable_delta", nullable = false) private long withdrawableDelta;
    @Column(name = "event_key", nullable = false, unique = true, length = 120) private String eventKey;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected DriverLedgerEntity() {}

    public DriverLedgerEntity(String ledgerNo, Long driverId, Long orderId, Long withdrawalId, LedgerType ledgerType,
            long amount, long availableBefore, long availableAfter, long frozenBefore, long frozenAfter,
            long businessIncomeAmount, long withdrawableDelta, String eventKey, Instant createdAt) {
        this.ledgerNo = ledgerNo;
        this.driverId = driverId;
        this.orderId = orderId;
        this.withdrawalId = withdrawalId;
        this.ledgerType = ledgerType;
        this.amount = amount;
        this.availableBefore = availableBefore;
        this.availableAfter = availableAfter;
        this.frozenBefore = frozenBefore;
        this.frozenAfter = frozenAfter;
        this.businessIncomeAmount = businessIncomeAmount;
        this.withdrawableDelta = withdrawableDelta;
        this.eventKey = eventKey;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getLedgerNo() { return ledgerNo; }
    public Long getDriverId() { return driverId; }
    public Long getOrderId() { return orderId; }
    public Long getWithdrawalId() { return withdrawalId; }
    public LedgerType getLedgerType() { return ledgerType; }
    public long getAmount() { return amount; }
    public long getAvailableBefore() { return availableBefore; }
    public long getAvailableAfter() { return availableAfter; }
    public long getFrozenBefore() { return frozenBefore; }
    public long getFrozenAfter() { return frozenAfter; }
    public long getBusinessIncomeAmount() { return businessIncomeAmount; }
    public long getWithdrawableDelta() { return withdrawableDelta; }
    public String getEventKey() { return eventKey; }
    public Instant getCreatedAt() { return createdAt; }
}
