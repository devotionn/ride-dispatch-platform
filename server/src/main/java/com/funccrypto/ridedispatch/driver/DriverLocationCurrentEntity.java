package com.funccrypto.ridedispatch.driver;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "driver_location_current")
public class DriverLocationCurrentEntity {

    @Id
    @Column(name = "driver_id")
    private Long driverId;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "accuracy_meters", precision = 10, scale = 2)
    private BigDecimal accuracyMeters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DriverLocationSource source;

    @Column(name = "located_at", nullable = false)
    private Instant locatedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected DriverLocationCurrentEntity() {
    }

    public DriverLocationCurrentEntity(
            Long driverId,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal accuracyMeters,
            DriverLocationSource source,
            Instant locatedAt,
            Instant receivedAt) {
        this.driverId = driverId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeters = accuracyMeters;
        this.source = source;
        this.locatedAt = locatedAt;
        this.receivedAt = receivedAt;
    }

    public Long getDriverId() {
        return driverId;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public Instant getLocatedAt() {
        return locatedAt;
    }
}
