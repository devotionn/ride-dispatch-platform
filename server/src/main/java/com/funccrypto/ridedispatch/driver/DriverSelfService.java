package com.funccrypto.ridedispatch.driver;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverSelfService {

    private static final Duration MAX_FUTURE_CLOCK_SKEW = Duration.ofMinutes(2);

    private final DriverRepository driverRepository;
    private final DriverLocationCurrentRepository locationRepository;
    private final VehicleRepository vehicleRepository;
    private final QrCodeRenderer qrCodeRenderer;
    private final Clock clock;

    public DriverSelfService(
            DriverRepository driverRepository,
            DriverLocationCurrentRepository locationRepository,
            VehicleRepository vehicleRepository,
            QrCodeRenderer qrCodeRenderer,
            Clock clock) {
        this.driverRepository = driverRepository;
        this.locationRepository = locationRepository;
        this.vehicleRepository = vehicleRepository;
        this.qrCodeRenderer = qrCodeRenderer;
        this.clock = clock;
    }

    @Transactional
    public DriverStateView updateWorkStatus(Long driverId, DriverWorkStatus workStatus) {
        DriverEntity driver = requireDriver(driverId);
        driver.updateWorkStatus(workStatus, clock.instant());
        return DriverStateView.from(driver);
    }

    @Transactional(readOnly = true)
    public DriverStateView getState(Long driverId) {
        return DriverStateView.from(requireDriver(driverId));
    }

    @Transactional
    public DriverStateView updateAvailablePassengers(Long driverId, int availablePassengers) {
        DriverEntity driver = requireDriver(driverId);
        driver.updateAvailablePassengers(availablePassengers, clock.instant());
        return DriverStateView.from(driver);
    }

    @Transactional
    public LocationView updateLocation(
            Long driverId,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal accuracyMeters,
            Instant locatedAt,
            DriverLocationSource source) {
        DriverEntity driver = requireDriver(driverId);
        if (driver.getAccountStatus() != DriverAccountStatus.ACTIVE) {
            throw new BusinessException("DRIVER_DISABLED", "司机账号已停用");
        }
        Instant now = clock.instant();
        if (locatedAt.isAfter(now.plus(MAX_FUTURE_CLOCK_SKEW))) {
            throw new BusinessException("DRIVER_LOCATION_IN_FUTURE", "定位时间异常");
        }
        DriverLocationCurrentEntity location = locationRepository.findById(driverId)
                .orElseGet(() -> new DriverLocationCurrentEntity(
                        driverId, latitude, longitude, accuracyMeters, source, locatedAt, now));
        location.update(latitude, longitude, accuracyMeters, source, locatedAt, now);
        locationRepository.save(location);
        return new LocationView(driverId, latitude, longitude, locatedAt, now);
    }

    @Transactional(readOnly = true)
    public QrView getQr(Long driverId) {
        DriverEntity driver = requireDriver(driverId);
        String path = "/ride/d/" + driver.getQrShortCode();
        return new QrView(driver.getQrShortCode(), path, qrCodeRenderer.dataUrl(path));
    }

    @Transactional(readOnly = true)
    public ProfileView getProfile(Long driverId) {
        DriverEntity driver = requireDriver(driverId);
        VehicleEntity vehicle = driver.getDefaultVehicleId() == null ? null
                : vehicleRepository.findById(driver.getDefaultVehicleId()).orElse(null);
        return new ProfileView(driver.getId(), driver.getDriverNo(), driver.getName(), driver.getMobile(),
                driver.getAccountStatus(), vehicle == null ? null : vehicle.getPlateNo(),
                vehicle == null ? null : vehicle.getBrandModel());
    }

    private DriverEntity requireDriver(Long driverId) {
        return driverRepository.findById(driverId)
                .orElseThrow(() -> new BusinessException("DRIVER_NOT_FOUND", "司机不存在"));
    }

    public record DriverStateView(
            Long driverId,
            DriverWorkStatus workStatus,
            int availablePassengers,
            int maxPassengers) {
        static DriverStateView from(DriverEntity driver) {
            return new DriverStateView(
                    driver.getId(), driver.getWorkStatus(),
                    driver.getAvailablePassengers(), driver.getMaxPassengers());
        }
    }

    public record LocationView(
            Long driverId,
            BigDecimal latitude,
            BigDecimal longitude,
            Instant locatedAt,
            Instant receivedAt) {
    }

    public record QrView(String shortCode, String path, String imageDataUrl) {
    }

    public record ProfileView(Long driverId, String driverNo, String name, String mobile,
            DriverAccountStatus accountStatus, String plateNo, String brandModel) {
    }
}
