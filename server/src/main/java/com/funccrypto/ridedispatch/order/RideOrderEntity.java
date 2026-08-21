package com.funccrypto.ridedispatch.order;

import java.math.BigDecimal;
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
@Table(name = "ride_order")
public class RideOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, unique = true, length = 40)
    private String orderNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private OrderSourceType sourceType;

    @Column(name = "source_driver_id")
    private Long sourceDriverId;

    @Column(name = "current_driver_id")
    private Long currentDriverId;

    @Column(name = "passenger_mobile", nullable = false, length = 30)
    private String passengerMobile;

    @Column(name = "passenger_access_token_hash", nullable = false, length = 64)
    private String passengerAccessTokenHash;

    @Column(name = "pickup_address", nullable = false, length = 255)
    private String pickupAddress;

    @Column(name = "pickup_latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal pickupLatitude;

    @Column(name = "pickup_longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal pickupLongitude;

    @Column(name = "destination_address", nullable = false, length = 255)
    private String destinationAddress;

    @Column(name = "destination_latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal destinationLatitude;

    @Column(name = "destination_longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal destinationLongitude;

    @Column(name = "passenger_count", nullable = false)
    private int passengerCount;

    @Column(name = "departure_at", nullable = false)
    private Instant departureAt;

    @Column(length = 500)
    private String remark;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trip_stage", length = 40)
    private TripStage tripStage;

    @Column(name = "final_amount")
    private Long finalAmount;

    @Column(name = "settlement_method", length = 30)
    private String settlementMethod;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "service_started_at")
    private Instant serviceStartedAt;

    @Column(name = "arrived_destination_at")
    private Instant arrivedDestinationAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected RideOrderEntity() {
    }

    public RideOrderEntity(
            String orderNo,
            OrderSourceType sourceType,
            Long sourceDriverId,
            String passengerMobile,
            String passengerAccessTokenHash,
            String pickupAddress,
            BigDecimal pickupLatitude,
            BigDecimal pickupLongitude,
            String destinationAddress,
            BigDecimal destinationLatitude,
            BigDecimal destinationLongitude,
            int passengerCount,
            Instant departureAt,
            String remark,
            OrderStatus initialStatus,
            Instant now) {
        this.orderNo = orderNo;
        this.sourceType = sourceType;
        this.sourceDriverId = sourceDriverId;
        this.passengerMobile = passengerMobile;
        this.passengerAccessTokenHash = passengerAccessTokenHash;
        this.pickupAddress = pickupAddress;
        this.pickupLatitude = pickupLatitude;
        this.pickupLongitude = pickupLongitude;
        this.destinationAddress = destinationAddress;
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;
        this.passengerCount = passengerCount;
        this.departureAt = departureAt;
        this.remark = remark;
        this.status = initialStatus;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void markPendingDriverConfirmation(Instant now) {
        requireStatus(OrderStatus.PENDING_DISPATCH);
        this.status = OrderStatus.PENDING_DRIVER_CONFIRM;
        this.updatedAt = now;
    }

    public void accept(Long driverId, Instant now) {
        requireStatus(OrderStatus.PENDING_DRIVER_CONFIRM);
        this.currentDriverId = driverId;
        this.status = OrderStatus.ACCEPTED;
        this.acceptedAt = now;
        this.updatedAt = now;
    }

    public void rejectedByTargetDriver(Instant now) {
        requireStatus(OrderStatus.PENDING_DRIVER_CONFIRM);
        this.status = OrderStatus.PENDING_DISPATCH;
        this.updatedAt = now;
    }

    public void cancelBeforeAcceptance(Instant now) {
        if (status != OrderStatus.PENDING_DISPATCH && status != OrderStatus.PENDING_DRIVER_CONFIRM) {
            throw new BusinessException("ORDER_CANNOT_BE_CANCELLED", "司机接单后乘客不能自行取消");
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = now;
        this.updatedAt = now;
    }

    public void advanceTrip(Long actingDriverId, TripStage nextStage, Instant now) {
        requireCurrentDriver(actingDriverId);
        if (nextStage == null) {
            throw new BusinessException("TRIP_STAGE_REQUIRED", "履约阶段不能为空");
        }

        if (status == OrderStatus.ACCEPTED && tripStage == null && nextStage == TripStage.ARRIVED_PICKUP) {
            this.status = OrderStatus.IN_SERVICE;
            this.tripStage = nextStage;
            this.serviceStartedAt = now;
            this.updatedAt = now;
            return;
        }
        requireStatus(OrderStatus.IN_SERVICE);

        TripStage expected = switch (tripStage) {
            case ARRIVED_PICKUP -> TripStage.PASSENGER_ONBOARD;
            case PASSENGER_ONBOARD -> TripStage.IN_TRANSIT;
            case IN_TRANSIT -> TripStage.ARRIVED_DESTINATION;
            case ARRIVED_DESTINATION -> null;
            case null -> null;
        };
        if (expected == null || expected != nextStage) {
            throw new BusinessException("TRIP_STAGE_CONFLICT", "履约阶段必须按顺序推进");
        }

        this.tripStage = nextStage;
        if (nextStage == TripStage.ARRIVED_DESTINATION) {
            this.arrivedDestinationAt = now;
        }
        this.updatedAt = now;
    }

    public void submitFinalAmount(Long actingDriverId, long amount, Instant now) {
        requireCurrentDriver(actingDriverId);
        requireStatus(OrderStatus.IN_SERVICE);
        if (tripStage != TripStage.ARRIVED_DESTINATION) {
            throw new BusinessException("ORDER_NOT_ARRIVED_DESTINATION", "到达目的地后才能录入最终金额");
        }
        if (amount <= 0) {
            throw new BusinessException("INVALID_FINAL_AMOUNT", "最终金额必须大于 0");
        }
        this.finalAmount = amount;
        this.status = OrderStatus.PENDING_PAYMENT;
        this.updatedAt = now;
    }

    private void requireCurrentDriver(Long actingDriverId) {
        if (currentDriverId == null || !currentDriverId.equals(actingDriverId)) {
            throw new BusinessException("ORDER_NOT_CURRENT_DRIVER", "当前订单不属于该司机");
        }
    }

    private void requireStatus(OrderStatus expected) {
        if (status != expected) {
            throw new BusinessException("ORDER_STATE_CONFLICT", "订单状态已变化，请刷新后重试");
        }
    }

    public Long getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public OrderSourceType getSourceType() { return sourceType; }
    public Long getSourceDriverId() { return sourceDriverId; }
    public Long getCurrentDriverId() { return currentDriverId; }
    public String getPassengerMobile() { return passengerMobile; }
    public String getPassengerAccessTokenHash() { return passengerAccessTokenHash; }
    public String getPickupAddress() { return pickupAddress; }
    public BigDecimal getPickupLatitude() { return pickupLatitude; }
    public BigDecimal getPickupLongitude() { return pickupLongitude; }
    public String getDestinationAddress() { return destinationAddress; }
    public BigDecimal getDestinationLatitude() { return destinationLatitude; }
    public BigDecimal getDestinationLongitude() { return destinationLongitude; }
    public int getPassengerCount() { return passengerCount; }
    public Instant getDepartureAt() { return departureAt; }
    public String getRemark() { return remark; }
    public OrderStatus getStatus() { return status; }
    public TripStage getTripStage() { return tripStage; }
    public Long getFinalAmount() { return finalAmount; }
    public String getSettlementMethod() { return settlementMethod; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getServiceStartedAt() { return serviceStartedAt; }
    public Instant getArrivedDestinationAt() { return arrivedDestinationAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
