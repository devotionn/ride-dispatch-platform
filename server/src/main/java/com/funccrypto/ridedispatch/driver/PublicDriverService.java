package com.funccrypto.ridedispatch.driver;

import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicDriverService {

    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;

    public PublicDriverService(DriverRepository driverRepository, VehicleRepository vehicleRepository) {
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @Transactional(readOnly = true)
    public PublicDriverView getByQrShortCode(String shortCode) {
        DriverEntity driver = driverRepository.findByQrShortCode(shortCode)
                .filter(item -> item.getAccountStatus() == DriverAccountStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException("DRIVER_NOT_FOUND", "司机二维码已失效"));

        VehicleEntity vehicle = driver.getDefaultVehicleId() == null
                ? null
                : vehicleRepository.findById(driver.getDefaultVehicleId()).orElse(null);

        return new PublicDriverView(
                driver.getName(),
                vehicle == null ? null : vehicle.getPlateNo(),
                vehicle == null ? null : vehicle.getBrandModel(),
                driver.getMaxPassengers());
    }

    public record PublicDriverView(
            String name,
            String plateNo,
            String brandModel,
            int maxPassengers) {
    }
}
