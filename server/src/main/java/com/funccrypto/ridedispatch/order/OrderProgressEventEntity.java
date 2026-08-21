package com.funccrypto.ridedispatch.order;

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
@Table(name = "order_progress_event")
public class OrderProgressEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TripStage stage;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected OrderProgressEventEntity() {
    }

    public OrderProgressEventEntity(Long orderId, Long driverId, TripStage stage, Instant occurredAt) {
        this.orderId = orderId;
        this.driverId = driverId;
        this.stage = stage;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getDriverId() {
        return driverId;
    }

    public TripStage getStage() {
        return stage;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
