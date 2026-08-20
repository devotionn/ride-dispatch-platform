package com.funccrypto.ridedispatch.driver;

import static org.assertj.core.api.Assertions.assertThat;

import com.funccrypto.ridedispatch.audit.OperationLogRepository;
import com.funccrypto.ridedispatch.auth.AuthSessionRepository;
import com.funccrypto.ridedispatch.dispatch.DispatchAttemptRepository;
import com.funccrypto.ridedispatch.order.RideOrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DriverAdminServiceIntegrationTest {

    @Autowired
    DriverAdminService service;

    @Autowired
    DriverRepository driverRepository;

    @Autowired
    VehicleRepository vehicleRepository;

    @Autowired
    DriverLocationCurrentRepository locationRepository;

    @Autowired
    RideOrderRepository orderRepository;

    @Autowired
    DispatchAttemptRepository attemptRepository;

    @Autowired
    OperationLogRepository operationLogRepository;

    @Autowired
    AuthSessionRepository sessionRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void beforeEach() {
        cleanDatabase();
    }

    @AfterEach
    void afterEach() {
        cleanDatabase();
    }

    @Test
    void createsDriverVehiclePasswordAndQrCode() {
        DriverAdminService.DriverView result = service.create(new DriverAdminService.CreateDriverCommand(
                "D900", "测试司机", "13800000900", "driver-password", 4, 4,
                "苏KTEST01", "测试车型"), 1L, "test-create-driver");

        DriverEntity driver = driverRepository.findById(result.id()).orElseThrow();
        VehicleEntity vehicle = vehicleRepository.findById(result.vehicleId()).orElseThrow();

        assertThat(passwordEncoder.matches("driver-password", driver.getPasswordHash())).isTrue();
        assertThat(driver.getQrShortCode()).isNotBlank();
        assertThat(driver.getDefaultVehicleId()).isEqualTo(vehicle.getId());
        assertThat(vehicle.getDriverId()).isEqualTo(driver.getId());
        assertThat(operationLogRepository.count()).isEqualTo(1);
    }

    private void cleanDatabase() {
        sessionRepository.deleteAll();
        operationLogRepository.deleteAll();
        attemptRepository.deleteAll();
        orderRepository.deleteAll();
        locationRepository.deleteAll();
        vehicleRepository.deleteAll();
        driverRepository.deleteAll();
    }
}
