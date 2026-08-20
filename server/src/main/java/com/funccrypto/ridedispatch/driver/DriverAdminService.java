package com.funccrypto.ridedispatch.driver;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.funccrypto.ridedispatch.audit.AuditService;
import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriverAdminService {

    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverQrShortCodeService qrShortCodeService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final Clock clock;

    public DriverAdminService(
            DriverRepository driverRepository,
            VehicleRepository vehicleRepository,
            DriverQrShortCodeService qrShortCodeService,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            Clock clock) {
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
        this.qrShortCodeService = qrShortCodeService;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public DriverView create(CreateDriverCommand command, Long operatorId, String requestId) {
        if (driverRepository.existsByDriverNo(command.driverNo())) {
            throw new BusinessException("DRIVER_NO_DUPLICATE", "司机工号已存在");
        }
        if (vehicleRepository.existsByPlateNo(command.plateNo())) {
            throw new BusinessException("VEHICLE_PLATE_DUPLICATE", "车牌号已存在");
        }

        Instant now = clock.instant();
        String qrShortCode = uniqueShortCode();
        DriverEntity driver = driverRepository.save(DriverEntity.createWithPassword(
                command.driverNo(),
                command.name(),
                command.mobile(),
                passwordEncoder.encode(command.password()),
                command.maxPassengers(),
                command.availablePassengers(),
                qrShortCode,
                now));
        VehicleEntity vehicle = vehicleRepository.save(new VehicleEntity(
                driver.getId(),
                command.plateNo(),
                command.brandModel(),
                command.maxPassengers(),
                now));
        driver.assignDefaultVehicle(vehicle.getId(), now);

        DriverView result = DriverView.from(driver, vehicle);
        auditService.log(
                "ADMIN", operatorId, "DRIVER", driver.getId().toString(), "DRIVER_CREATED",
                null,
                Map.of("driverNo", driver.getDriverNo(), "plateNo", vehicle.getPlateNo()),
                null, requestId, now);
        return result;
    }

    @Transactional(readOnly = true)
    public List<DriverView> list() {
        return driverRepository.findAllByOrderByIdDesc().stream()
                .map(driver -> DriverView.from(
                        driver,
                        driver.getDefaultVehicleId() == null
                                ? null
                                : vehicleRepository.findById(driver.getDefaultVehicleId()).orElse(null)))
                .toList();
    }

    private String uniqueShortCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = qrShortCodeService.generate();
            if (!driverRepository.existsByQrShortCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Unable to allocate driver QR short code");
    }

    public record CreateDriverCommand(
            String driverNo,
            String name,
            String mobile,
            String password,
            int maxPassengers,
            int availablePassengers,
            String plateNo,
            String brandModel) {
    }

    public record DriverView(
            Long id,
            String driverNo,
            String name,
            String mobile,
            DriverAccountStatus accountStatus,
            DriverWorkStatus workStatus,
            int maxPassengers,
            int availablePassengers,
            String qrShortCode,
            Long vehicleId,
            String plateNo,
            String brandModel) {
        static DriverView from(DriverEntity driver, VehicleEntity vehicle) {
            return new DriverView(
                    driver.getId(), driver.getDriverNo(), driver.getName(), driver.getMobile(),
                    driver.getAccountStatus(), driver.getWorkStatus(), driver.getMaxPassengers(),
                    driver.getAvailablePassengers(), driver.getQrShortCode(),
                    vehicle == null ? null : vehicle.getId(),
                    vehicle == null ? null : vehicle.getPlateNo(),
                    vehicle == null ? null : vehicle.getBrandModel());
        }
    }
}
