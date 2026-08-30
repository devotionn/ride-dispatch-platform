package com.funccrypto.ridedispatch.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.funccrypto.ridedispatch.driver.DriverEntity;
import com.funccrypto.ridedispatch.driver.DriverLocationCurrentEntity;
import com.funccrypto.ridedispatch.driver.DriverLocationCurrentRepository;
import com.funccrypto.ridedispatch.driver.DriverLocationSource;
import com.funccrypto.ridedispatch.driver.DriverRepository;
import com.funccrypto.ridedispatch.order.OrderSourceType;
import com.funccrypto.ridedispatch.order.PublicOrderService;
import com.funccrypto.ridedispatch.order.RideOrderRepository;
import com.funccrypto.ridedispatch.payment.PaymentAttemptRepository;
import com.funccrypto.ridedispatch.payment.PaymentExceptionRepository;
import com.funccrypto.ridedispatch.payment.PaymentRepository;
import com.funccrypto.ridedispatch.settlement.DriverAccountRepository;
import com.funccrypto.ridedispatch.settlement.DriverLedgerRepository;
import com.funccrypto.ridedispatch.settlement.WithdrawalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class NearbyDriverServiceIntegrationTest {

    @Autowired
    NearbyDriverService nearbyDriverService;

    @Autowired
    PublicOrderService publicOrderService;

    @Autowired
    DriverRepository driverRepository;

    @Autowired
    DriverLocationCurrentRepository locationRepository;

    @Autowired
    RideOrderRepository orderRepository;

    @Autowired
    DispatchAttemptRepository attemptRepository;
    @Autowired PaymentAttemptRepository paymentAttemptRepository;
    @Autowired PaymentExceptionRepository paymentExceptionRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired DriverLedgerRepository driverLedgerRepository;
    @Autowired WithdrawalRepository withdrawalRepository;
    @Autowired DriverAccountRepository driverAccountRepository;

    @BeforeEach
    void clean() {
        driverLedgerRepository.deleteAll();
        withdrawalRepository.deleteAll();
        driverAccountRepository.deleteAll();
        paymentExceptionRepository.deleteAll();
        paymentAttemptRepository.deleteAll();
        paymentRepository.deleteAll();
        attemptRepository.deleteAll();
        orderRepository.deleteAll();
        locationRepository.deleteAll();
        driverRepository.deleteAll();
    }

    @Test
    void returnsOnlyFreshDriversWithinTenKmSortedByDistance() {
        Instant now = Instant.now();
        DriverEntity near = driverRepository.save(DriverEntity.create(
                "D201", "近司机", "13800000201", 4, 4, "QRD201", now));
        DriverEntity stale = driverRepository.save(DriverEntity.create(
                "D202", "旧定位司机", "13800000202", 4, 4, "QRD202", now));
        DriverEntity far = driverRepository.save(DriverEntity.create(
                "D203", "远司机", "13800000203", 4, 4, "QRD203", now));

        locationRepository.save(location(near.getId(), "32.3920000", "119.5080000", now.minusSeconds(60)));
        locationRepository.save(location(stale.getId(), "32.3930000", "119.5080000", now.minusSeconds(600)));
        locationRepository.save(location(far.getId(), "32.6000000", "119.5080000", now.minusSeconds(60)));

        PublicOrderService.CreateOrderResult order = publicOrderService.create(new PublicOrderService.CreateOrderCommand(
                OrderSourceType.PUBLIC_H5,
                null,
                "扬州东站",
                new BigDecimal("32.3910000"),
                new BigDecimal("119.5080000"),
                "瘦西湖",
                new BigDecimal("32.4200000"),
                new BigDecimal("119.4140000"),
                2,
                now.plusSeconds(3600),
                "13800000000",
                null));

        List<NearbyDriverView> result = nearbyDriverService.findNearby(order.orderNo());

        assertThat(result).extracting(NearbyDriverView::driverId).containsExactly(near.getId());
    }

    @Test
    void textOnlyPickupReturnsEligibleDriversWithoutDistanceCalculation() {
        Instant now = Instant.now();
        DriverEntity driver = driverRepository.save(DriverEntity.create(
                "D204", "人工派单司机", "13800000204", 4, 4, "QRD204", now));
        PublicOrderService.CreateOrderResult order = publicOrderService.create(new PublicOrderService.CreateOrderCommand(
                OrderSourceType.PUBLIC_H5, null, "扬州东站北广场", null, null,
                "瘦西湖", null, null, 2, now.plusSeconds(3600), "13800000000", null));

        List<NearbyDriverView> result = nearbyDriverService.findNearby(order.orderNo());

        assertThat(result).singleElement().satisfies(candidate -> {
            assertThat(candidate.driverId()).isEqualTo(driver.getId());
            assertThat(candidate.straightLineDistanceKm()).isNull();
            assertThat(candidate.locatedAt()).isNull();
        });
    }

    @Test
    void haversineDistanceRemainsFiniteAtAntipodalBoundary() {
        double distance = NearbyDriverService.haversineKm(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("180"));

        assertThat(distance).isFinite();
    }

    private DriverLocationCurrentEntity location(Long driverId, String lat, String lng, Instant locatedAt) {
        return new DriverLocationCurrentEntity(
                driverId,
                new BigDecimal(lat),
                new BigDecimal(lng),
                new BigDecimal("10.0"),
                DriverLocationSource.DRIVER_APP,
                locatedAt,
                Instant.now());
    }
}
