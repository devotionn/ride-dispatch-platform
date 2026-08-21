package com.funccrypto.ridedispatch.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import com.funccrypto.ridedispatch.dispatch.DispatchAttemptRepository;
import com.funccrypto.ridedispatch.dispatch.DispatchAttemptStatus;
import com.funccrypto.ridedispatch.driver.DriverEntity;
import com.funccrypto.ridedispatch.driver.DriverRepository;
import com.funccrypto.ridedispatch.shared.error.BusinessException;
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
    PassengerOrderAccessTokenRepository passengerTokenRepository;

    @Autowired
    DispatchAttemptRepository attemptRepository;

    @Autowired
    DriverRepository driverRepository;

    @BeforeEach
    void clean() {
        passengerTokenRepository.deleteAll();
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

    @Test
    void repeatedIdempotentRequestReturnsSameOrderAndBothTokensRemainValid() {
        PublicOrderService.CreateOrderCommand command = command(OrderSourceType.PUBLIC_H5, null);
        String key = "order-test-1234567890abcdef";

        PublicOrderService.CreateOrderResult first = service.create(command, key);
        PublicOrderService.CreateOrderResult second = service.create(command, key);

        assertThat(second.orderNo()).isEqualTo(first.orderNo());
        assertThat(second.passengerAccessToken()).isNotEqualTo(first.passengerAccessToken());
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(passengerTokenRepository.count()).isEqualTo(1);
        assertThat(service.getForPassenger(first.orderNo(), first.passengerAccessToken()).getOrderNo())
                .isEqualTo(first.orderNo());
        assertThat(service.getForPassenger(first.orderNo(), second.passengerAccessToken()).getOrderNo())
                .isEqualTo(first.orderNo());
    }

    @Test
    void sameIdempotencyKeyCannotRepresentDifferentOrderPayloads() {
        String key = "order-test-abcdef1234567890";
        PublicOrderService.CreateOrderCommand first = command(OrderSourceType.PUBLIC_H5, null);
        service.create(first, key);

        PublicOrderService.CreateOrderCommand changed = new PublicOrderService.CreateOrderCommand(
                first.sourceType(), first.driverShortCode(), first.pickupAddress(), first.pickupLatitude(),
                first.pickupLongitude(), first.destinationAddress(), first.destinationLatitude(),
                first.destinationLongitude(), 3, first.departureAt(), first.passengerMobile(), first.remark());

        assertThatThrownBy(() -> service.create(changed, key))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同一幂等键");
        assertThat(orderRepository.count()).isEqualTo(1);
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
