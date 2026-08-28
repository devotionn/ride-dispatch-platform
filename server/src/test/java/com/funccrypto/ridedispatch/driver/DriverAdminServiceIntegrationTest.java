package com.funccrypto.ridedispatch.driver;

import static org.assertj.core.api.Assertions.assertThat;

import com.funccrypto.ridedispatch.audit.OperationLogRepository;
import com.funccrypto.ridedispatch.auth.AuthSessionRepository;
import com.funccrypto.ridedispatch.dispatch.DispatchAttemptRepository;
import com.funccrypto.ridedispatch.order.RideOrderRepository;
import com.funccrypto.ridedispatch.payment.PaymentAttemptRepository;
import com.funccrypto.ridedispatch.payment.PaymentExceptionRepository;
import com.funccrypto.ridedispatch.payment.PaymentRepository;
import com.funccrypto.ridedispatch.settlement.DriverAccountRepository;
import com.funccrypto.ridedispatch.settlement.DriverLedgerRepository;
import com.funccrypto.ridedispatch.settlement.WithdrawalRepository;
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

    @Autowired PaymentAttemptRepository paymentAttemptRepository;
    @Autowired PaymentExceptionRepository paymentExceptionRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired DriverLedgerRepository driverLedgerRepository;
    @Autowired WithdrawalRepository withdrawalRepository;
    @Autowired DriverAccountRepository driverAccountRepository;

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

    @Test
    void editsDriverAndCanDisableAndReenableAccount() {
        DriverAdminService.DriverView created = service.create(new DriverAdminService.CreateDriverCommand(
                "D901", "原司机", "13800000901", "driver-password", 4, 4,
                "苏KTEST02", "旧车型"), 1L, "test-create-driver");

        DriverAdminService.DriverView updated = service.update(created.id(), new DriverAdminService.UpdateDriverCommand(
                "新司机", "13800000902", null, 6, 3, "苏KTEST03", "新车型"), 1L, "test-update-driver");
        assertThat(updated.name()).isEqualTo("新司机");
        assertThat(updated.mobile()).isEqualTo("13800000902");
        assertThat(updated.maxPassengers()).isEqualTo(6);
        assertThat(updated.availablePassengers()).isEqualTo(3);
        assertThat(updated.plateNo()).isEqualTo("苏KTEST03");

        DriverAdminService.DriverView disabled = service.updateAccountStatus(created.id(), DriverAccountStatus.DISABLED, 1L, "test-disable-driver");
        assertThat(disabled.accountStatus()).isEqualTo(DriverAccountStatus.DISABLED);
        assertThat(disabled.workStatus()).isEqualTo(DriverWorkStatus.OFFLINE);

        DriverAdminService.DriverView reenabled = service.updateAccountStatus(created.id(), DriverAccountStatus.ACTIVE, 1L, "test-enable-driver");
        assertThat(reenabled.accountStatus()).isEqualTo(DriverAccountStatus.ACTIVE);
        assertThat(reenabled.workStatus()).isEqualTo(DriverWorkStatus.OFFLINE);
        assertThat(operationLogRepository.count()).isEqualTo(4);
    }

    @Test
    void detailIncludesOperationalAndSettlementSnapshots() {
        DriverAdminService.DriverView created = service.create(new DriverAdminService.CreateDriverCommand(
                "D902", "详情司机", "13800000902", "driver-password", 4, 4,
                "苏KTEST04", "详情车型"), 1L, "test-detail-driver");

        DriverAdminService.DriverDetailView detail = service.detail(created.id());

        assertThat(detail.driver().driverNo()).isEqualTo("D902");
        assertThat(detail.activeOrders()).isEmpty();
        assertThat(detail.historyOrders()).isEmpty();
        assertThat(detail.completedOrderCount()).isZero();
        assertThat(detail.businessIncome()).isZero();
        assertThat(detail.availableBalance()).isZero();
        assertThat(detail.withdrawals()).isEmpty();
    }

    private void cleanDatabase() {
        sessionRepository.deleteAll();
        operationLogRepository.deleteAll();
        driverLedgerRepository.deleteAll();
        withdrawalRepository.deleteAll();
        driverAccountRepository.deleteAll();
        paymentExceptionRepository.deleteAll();
        paymentAttemptRepository.deleteAll();
        paymentRepository.deleteAll();
        attemptRepository.deleteAll();
        orderRepository.deleteAll();
        locationRepository.deleteAll();
        vehicleRepository.deleteAll();
        driverRepository.deleteAll();
    }
}
