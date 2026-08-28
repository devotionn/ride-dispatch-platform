package com.funccrypto.ridedispatch.driver;

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
@Table(name = "vehicle")
public class VehicleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "driver_id", nullable = false)
    private Long driverId;

    @Column(name = "plate_no", nullable = false, unique = true, length = 32)
    private String plateNo;

    @Column(name = "brand_model", length = 120)
    private String brandModel;

    @Column(name = "max_passengers", nullable = false)
    private int maxPassengers;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VehicleStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VehicleEntity() {
    }

    public VehicleEntity(
            Long driverId,
            String plateNo,
            String brandModel,
            int maxPassengers,
            Instant now) {
        this.driverId = driverId;
        this.plateNo = plateNo;
        this.brandModel = brandModel;
        this.maxPassengers = maxPassengers;
        this.status = VehicleStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getDriverId() {
        return driverId;
    }

    public String getPlateNo() {
        return plateNo;
    }

    public String getBrandModel() {
        return brandModel;
    }

    public int getMaxPassengers() {
        return maxPassengers;
    }

    public void update(String plateNo, String brandModel, int maxPassengers, Instant now) {
        this.plateNo = plateNo;
        this.brandModel = brandModel;
        this.maxPassengers = maxPassengers;
        this.updatedAt = now;
    }

    public VehicleStatus getStatus() {
        return status;
    }
}
