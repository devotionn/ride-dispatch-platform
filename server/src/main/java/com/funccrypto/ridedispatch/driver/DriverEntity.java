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
import jakarta.persistence.Version;

import com.funccrypto.ridedispatch.shared.error.BusinessException;

@Entity
@Table(name = "driver")
public class DriverEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "driver_no", nullable = false, unique = true, length = 50)
    private String driverNo;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 30)
    private String mobile;

    @Column(name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 30)
    private DriverAccountStatus accountStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_status", nullable = false, length = 30)
    private DriverWorkStatus workStatus;

    @Column(name = "max_passengers", nullable = false)
    private int maxPassengers;

    @Column(name = "available_passengers", nullable = false)
    private int availablePassengers;

    @Column(name = "qr_short_code", nullable = false, unique = true, length = 32)
    private String qrShortCode;

    @Column(name = "default_vehicle_id")
    private Long defaultVehicleId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected DriverEntity() {
    }

    private DriverEntity(
            String driverNo,
            String name,
            String mobile,
            String passwordHash,
            int maxPassengers,
            int availablePassengers,
            String qrShortCode,
            Instant now) {
        this.driverNo = driverNo;
        this.name = name;
        this.mobile = mobile;
        this.passwordHash = passwordHash;
        this.accountStatus = DriverAccountStatus.ACTIVE;
        this.workStatus = DriverWorkStatus.AVAILABLE;
        this.maxPassengers = maxPassengers;
        this.availablePassengers = availablePassengers;
        this.qrShortCode = qrShortCode;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static DriverEntity create(
            String driverNo,
            String name,
            String mobile,
            int maxPassengers,
            int availablePassengers,
            String qrShortCode,
            Instant now) {
        return createWithPassword(
                driverNo, name, mobile, null, maxPassengers, availablePassengers, qrShortCode, now);
    }

    public static DriverEntity createWithPassword(
            String driverNo,
            String name,
            String mobile,
            String passwordHash,
            int maxPassengers,
            int availablePassengers,
            String qrShortCode,
            Instant now) {
        if (maxPassengers < 1 || availablePassengers < 0 || availablePassengers > maxPassengers) {
            throw new BusinessException("DRIVER_CAPACITY_INVALID", "司机可接人数配置不合法");
        }
        return new DriverEntity(
                driverNo, name, mobile, passwordHash, maxPassengers, availablePassengers, qrShortCode, now);
    }

    public boolean canReceiveNewOrder(int passengerCount) {
        return accountStatus == DriverAccountStatus.ACTIVE
                && workStatus == DriverWorkStatus.AVAILABLE
                && availablePassengers >= passengerCount;
    }

    public void assignDefaultVehicle(Long vehicleId, Instant now) {
        this.defaultVehicleId = vehicleId;
        this.updatedAt = now;
    }

    public void updateWorkStatus(DriverWorkStatus workStatus, Instant now) {
        if (accountStatus != DriverAccountStatus.ACTIVE) {
            throw new BusinessException("DRIVER_DISABLED", "司机账号已停用");
        }
        this.workStatus = workStatus;
        this.updatedAt = now;
    }

    public void updateAvailablePassengers(int availablePassengers, Instant now) {
        if (availablePassengers < 0 || availablePassengers > maxPassengers) {
            throw new BusinessException("DRIVER_CAPACITY_INVALID", "当前可接人数不能超过车辆最大载客人数");
        }
        this.availablePassengers = availablePassengers;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getDriverNo() {
        return driverNo;
    }

    public String getName() {
        return name;
    }

    public String getMobile() {
        return mobile;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public DriverAccountStatus getAccountStatus() {
        return accountStatus;
    }

    public DriverWorkStatus getWorkStatus() {
        return workStatus;
    }

    public int getMaxPassengers() {
        return maxPassengers;
    }

    public int getAvailablePassengers() {
        return availablePassengers;
    }

    public String getQrShortCode() {
        return qrShortCode;
    }

    public Long getDefaultVehicleId() {
        return defaultVehicleId;
    }
}
