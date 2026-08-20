package com.funccrypto.ridedispatch.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import com.funccrypto.ridedispatch.dispatch.DispatchAttemptRepository;
import com.funccrypto.ridedispatch.dispatch.DispatchAttemptStatus;
import com.funccrypto.ridedispatch.driver.DriverEntity;
import com.funccrypto.ridedispatch.driver.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PublicOrderServiceIntegrationTest {

    @Autowired
    PublicOrderService service;

    @Autowired
    RideOrderRepository orderRepository;

    @Autowired
    DispatchAttemptRepository attemptRepository;

    @Autowired
    DriverRepository driverRepository;

    @BeforeEach
    void clean() {
        attemptRepository.deleteAll();
        orderRepository.deleteAll();
        driverRepository.deleteAll();
    }

    @Test
    void publicOrderStartsPendingDispatch() {
        PublicOrderService.CreateOrderResult result = service.create(command(OrderSourceType.PUBLIC_H5, null));

        assertThat(result.status()).isEqualTo(OrderStatus.PENDING_DISPATCH);
        assertThat(result.passengerAccessToken()).isNotBlank();
        assertThat(orderRepository.findByOrderNo(result.orderNo())).isPresent();
    }

    @Test
    void driverQrOrderCreatesWaitingAttempt() {
        DriverEntity driver = driverRepository.save(DriverEntity.create(
                "D001", "张师傅", "13800000001", 4, 4, "QRD001", Instant.now()));

        PublicOrderService.CreateOrderResult result = service.create(command(OrderSourceType.DRIVER_QR, driver.getQrShortCode()));
        RideOrderEntity order = orderRepository.findByOrderNo(result.orderNo()).orElseThrow();

        assertThat(result.status()).isEqualTo(OrderStatus.PENDING_DRIVER_CONFIRM);
        assertThat(order.getSourceDriverId()).isEqualTo(driver.getId());
        assertThat(attemptRepository.findFirstByOrderIdAndStatusOrderByDispatchedAtDesc(
                order.getId(), DispatchAttemptStatus.WAITING)).isPresent();
    }

    private PublicOrderService.CreateOrderCommand command(OrderSourceType sourceType, String driverShortCode) {
        return new PublicOrderService.CreateOrderCommand(
                sourceType,
                driverShortCode,
                "扬州东站",
                new BigDecimal("32.3910000"),
                new BigDecimal("119.5080000"),
                "瘦西湖",
                new BigDecimal("32.4200000"),
                new BigDecimal("119.4140000"),
                2,
                Instant.now().plusSeconds(3600),
                "13800000000",
                null);
    }
}
