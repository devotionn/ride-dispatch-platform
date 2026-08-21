package com.funccrypto.ridedispatch.dispatch;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import com.funccrypto.ridedispatch.audit.OperationLogRepository;
import com.funccrypto.ridedispatch.driver.DriverEntity;
import com.funccrypto.ridedispatch.driver.DriverRepository;
import com.funccrypto.ridedispatch.order.OrderSourceType;
import com.funccrypto.ridedispatch.order.OrderStatus;
import com.funccrypto.ridedispatch.order.PublicOrderService;
import com.funccrypto.ridedispatch.order.RideOrderEntity;
import com.funccrypto.ridedispatch.order.RideOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DispatchServiceIntegrationTest {

    @Autowired DispatchService dispatchService;
    @Autowired PublicOrderService publicOrderService;
    @Autowired DispatchAttemptRepository attemptRepository;
    @Autowired RideOrderRepository orderRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired OperationLogRepository operationLogRepository;

    @BeforeEach
    void clean() {
        operationLogRepository.deleteAll();
        attemptRepository.deleteAll();
        orderRepository.deleteAll();
        driverRepository.deleteAll();
    }

    @Test
    void manualDispatchAndAcceptMovesOrderToAccepted() {
        DriverEntity driver = driver("D101", "李师傅", "13800000101", "QRD101");
        PublicOrderService.CreateOrderResult created = publicOrderService.create(publicCommand());
        DispatchAttemptEntity attempt = dispatchService.dispatch(created.orderNo(), driver.getId(), 9001L, "test-dispatch");
        OrderStatus accepted = dispatchService.accept(attempt.getId(), driver.getId(), "test-accept");
        RideOrderEntity order = orderRepository.findByOrderNo(created.orderNo()).orElseThrow();
        assertThat(accepted).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(order.getCurrentDriverId()).isEqualTo(driver.getId());
        assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().getStatus()).isEqualTo(DispatchAttemptStatus.ACCEPTED);
        assertThat(operationLogRepository.count()).isEqualTo(2);
    }

    @Test
    void rejectionReturnsOrderToPendingDispatch() {
        DriverEntity driver = driver("D102", "王师傅", "13800000102", "QRD102");
        PublicOrderService.CreateOrderResult created = publicOrderService.create(publicCommand());
        DispatchAttemptEntity attempt = dispatchService.dispatch(created.orderNo(), driver.getId(), 9001L, "test-dispatch");
        OrderStatus status = dispatchService.reject(attempt.getId(), driver.getId(), "VEHICLE_ISSUE", null, "test-reject");
        assertThat(status).isEqualTo(OrderStatus.PENDING_DISPATCH);
        assertThat(orderRepository.findByOrderNo(created.orderNo()).orElseThrow().getCurrentDriverId()).isNull();
    }

    @Test
    void pendingConfirmationCanBeReassignedWithoutLosingHistory() {
        DriverEntity first = driver("D103", "赵师傅", "13800000103", "QRD103");
        DriverEntity second = driver("D104", "钱师傅", "13800000104", "QRD104");
        PublicOrderService.CreateOrderResult created = publicOrderService.create(publicCommand());
        DispatchAttemptEntity firstAttempt = dispatchService.dispatch(created.orderNo(), first.getId(), 9001L, "first-dispatch");

        DispatchAttemptEntity replacement = dispatchService.reassignPending(
                created.orderNo(), second.getId(), 9001L, "调度调整", "reassign");

        RideOrderEntity order = orderRepository.findByOrderNo(created.orderNo()).orElseThrow();
        DispatchAttemptEntity invalidated = attemptRepository.findById(firstAttempt.getId()).orElseThrow();
        DispatchAttemptEntity newAttempt = attemptRepository.findById(replacement.getId()).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_DRIVER_CONFIRM);
        assertThat(invalidated.getStatus()).isEqualTo(DispatchAttemptStatus.CANCELLED_BY_REASSIGN);
        assertThat(newAttempt.getDispatchType()).isEqualTo(DispatchType.REASSIGN);
        assertThat(newAttempt.getTargetDriverId()).isEqualTo(second.getId());
        assertThat(newAttempt.getReassignFromDriverId()).isEqualTo(first.getId());
        assertThat(newAttempt.getReassignReason()).isEqualTo("调度调整");
        assertThat(attemptRepository.findByOrderIdOrderByDispatchedAtDesc(order.getId())).hasSize(2);
    }

    private DriverEntity driver(String no, String name, String mobile, String qr) {
        return driverRepository.save(DriverEntity.create(no, name, mobile, 4, 4, qr, Instant.now()));
    }

    private PublicOrderService.CreateOrderCommand publicCommand() {
        return new PublicOrderService.CreateOrderCommand(
                OrderSourceType.PUBLIC_H5, null, "扬州东站", new BigDecimal("32.3910000"), new BigDecimal("119.5080000"),
                "瘦西湖", new BigDecimal("32.4200000"), new BigDecimal("119.4140000"), 2,
                Instant.now().plusSeconds(3600), "13800000000", null);
    }
}
