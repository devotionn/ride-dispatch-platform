package com.funccrypto.ridedispatch.safety;

import java.time.Instant;

import com.funccrypto.ridedispatch.order.RideOrderEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "safety_alarm")
public class SafetyAlarmEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "order_id") private RideOrderEntity order;

    @Column(name = "order_no", length = 40) private String orderNo;

    @Column(name = "source_page", nullable = false, length = 40) private String sourcePage;

    @Column(precision = 10, scale = 7) private java.math.BigDecimal latitude;

    @Column(precision = 10, scale = 7) private java.math.BigDecimal longitude;

    @Column(name = "location_text", length = 255) private String locationText;

    @Column(name = "passenger_mobile", length = 30) private String passengerMobile;

    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected SafetyAlarmEntity() {
    }

    public SafetyAlarmEntity(
            RideOrderEntity order,
            String orderNo,
            String sourcePage,
            java.math.BigDecimal latitude,
            java.math.BigDecimal longitude,
            String locationText,
            String passengerMobile,
            Instant now) {
        this.order = order;
        this.orderNo = orderNo;
        this.sourcePage = sourcePage;
        this.latitude = latitude;
        this.longitude = longitude;
        this.locationText = locationText;
        this.passengerMobile = passengerMobile;
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public RideOrderEntity getOrder() { return order; }
    public String getOrderNo() { return orderNo; }
    public String getSourcePage() { return sourcePage; }
    public java.math.BigDecimal getLatitude() { return latitude; }
    public java.math.BigDecimal getLongitude() { return longitude; }
    public String getLocationText() { return locationText; }
    public String getPassengerMobile() { return passengerMobile; }
    public Instant getCreatedAt() { return createdAt; }
}
