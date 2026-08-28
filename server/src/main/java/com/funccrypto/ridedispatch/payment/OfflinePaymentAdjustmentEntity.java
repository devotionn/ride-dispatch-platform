package com.funccrypto.ridedispatch.payment;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "offline_payment_adjustment")
public class OfflinePaymentAdjustmentEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "adjustment_no", nullable = false, unique = true, length = 40) private String adjustmentNo;
    @Column(name = "payment_id", nullable = false) private Long paymentId;
    @Column(name = "order_id", nullable = false) private Long orderId;
    @Column(name = "driver_id", nullable = false) private Long driverId;
    @Column(name = "delta_amount", nullable = false) private long deltaAmount;
    @Column(nullable = false, length = 500) private String reason;
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 120) private String idempotencyKey;
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected OfflinePaymentAdjustmentEntity() {}

    public OfflinePaymentAdjustmentEntity(String adjustmentNo, Long paymentId, Long orderId, Long driverId,
            long deltaAmount, String reason, String idempotencyKey, Long createdBy, Instant createdAt) {
        this.adjustmentNo = adjustmentNo;
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.driverId = driverId;
        this.deltaAmount = deltaAmount;
        this.reason = reason;
        this.idempotencyKey = idempotencyKey;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getAdjustmentNo() { return adjustmentNo; }
    public Long getPaymentId() { return paymentId; }
    public Long getOrderId() { return orderId; }
    public Long getDriverId() { return driverId; }
    public long getDeltaAmount() { return deltaAmount; }
    public String getReason() { return reason; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Long getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
