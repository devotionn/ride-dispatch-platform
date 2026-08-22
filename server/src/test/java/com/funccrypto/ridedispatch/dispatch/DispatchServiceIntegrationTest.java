package com.funccrypto.ridedispatch.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import com.funccrypto.ridedispatch.audit.OperationLogRepository;
import com.funccrypto.ridedispatch.driver.DriverEntity;
import com.funccrypto.ridedispatch.driver.DriverRepository;
import com.funccrypto.ridedispatch.order.OrderSourceType;
import com.funccrypto.ridedispatch.order.OrderManagementService;
import com.funccrypto.ridedispatch.order.OrderProgressEventRepository;
import com.funccrypto.ridedispatch.order.OrderStatus;
import com.funccrypto.ridedispatch.order.PublicOrderService;
import com.funccrypto.ridedispatch.order.RideOrderEntity;
import com.funccrypto.ridedispatch.order.RideOrderRepository;
import com.funccrypto.ridedispatch.order.TripStage;
import com.funccrypto.ridedispatch.shared.error.BusinessException;
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
    @Autowired OrderManagementService orderManagementService;
    @Autowired OrderProgressEventRepository progressEventRepository;

    @BeforeEach
    void clean() {
        operationLogRepository.deleteAll();
        progressEventRepository.deleteAll();
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

    @Test
    void activeOrderCanBeForceCancelledWithReasonAndHistory() {
        DriverEntity driver = driver("D105", "孙师傅", "13800000105", "QRD105");
        PublicOrderService.CreateOrderResult created = publicOrderService.create(publicCommand());
        DispatchAttemptEntity attempt = dispatchService.dispatch(created.orderNo(), driver.getId(), 9001L, "dispatch");
        dispatchService.accept(attempt.getId(), driver.getId(), "accept");

        OrderStatus status = dispatchService.forceCancel(
                created.orderNo(), "乘客临时取消", 9001L, "force-cancel");

        RideOrderEntity order = orderRepository.findByOrderNo(created.orderNo()).orElseThrow();
        assertThat(status).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isNotNull();
        assertThat(attemptRepository.findById(attempt.getId()).orElseThrow().getStatus())
                .isEqualTo(DispatchAttemptStatus.ACCEPTED);
    }

    @Test
    void forceCancelRequiresReason() {
        DriverEntity driver = driver("D106", "周师傅", "13800000106", "QRD106");
        PublicOrderService.CreateOrderResult created = publicOrderService.create(publicCommand());
        DispatchAttemptEntity attempt = dispatchService.dispatch(created.orderNo(), driver.getId(), 9001L, "dispatch");
        dispatchService.accept(attempt.getId(), driver.getId(), "accept");

        assertThatThrownBy(() -> dispatchService.forceCancel(created.orderNo(), " ", 9001L, "force-cancel"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("FORCE_ACTION_REASON_REQUIRED"));
    }

    @Test
    void forcedReassignKeepsOriginalDriverResponsibleUntilAcceptance() {
        DriverEntity first = driver("D107", "吴师傅", "13800000107", "QRD107");
        DriverEntity second = driver("D108", "郑师傅", "13800000108", "QRD108");
        PublicOrderService.CreateOrderResult created = publicOrderService.create(publicCommand());
        DispatchAttemptEntity firstAttempt = dispatchService.dispatch(created.orderNo(), first.getId(), 9001L, "first");
        dispatchService.accept(firstAttempt.getId(), first.getId(), "accept");
        orderManagementService.advanceTrip(
                created.orderNo(), first.getId(), TripStage.ARRIVED_PICKUP, "progress");

        DispatchAttemptEntity replacement = dispatchService.forceReassign(
                created.orderNo(), second.getId(), "原司机车辆故障", 9001L, "force-reassign");

        RideOrderEntity order = orderRepository.findByOrderNo(created.orderNo()).orElseThrow();
        assertThat(order.getCurrentDriverId()).isEqualTo(first.getId());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_DRIVER_CONFIRM);
        assertThat(order.getTripStage()).isNull();
        assertThat(replacement.getDispatchType()).isEqualTo(DispatchType.FORCE_REASSIGN);
        assertThat(replacement.getTargetDriverId()).isEqualTo(second.getId());
        assertThat(replacement.getReassignFromDriverId()).isEqualTo(first.getId());

        OrderStatus accepted = dispatchService.accept(replacement.getId(), second.getId(), "second-accept");
        order = orderRepository.findByOrderNo(created.orderNo()).orElseThrow();
        assertThat(accepted).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(order.getCurrentDriverId()).isEqualTo(second.getId());
    }

    @Test
    void rejectedForcedReassignRestoresOriginalDriverResponsibility() {
        DriverEntity first = driver("D109", "冯师傅", "13800000109", "QRD109");
        DriverEntity second = driver("D110", "陈师傅", "13800000110", "QRD110");
        PublicOrderService.CreateOrderResult created = publicOrderService.create(publicCommand());
        DispatchAttemptEntity firstAttempt = dispatchService.dispatch(created.orderNo(), first.getId(), 9001L, "first");
        dispatchService.accept(firstAttempt.getId(), first.getId(), "accept");
        DispatchAttemptEntity replacement = dispatchService.forceReassign(
                created.orderNo(), second.getId(), "调度调整", 9001L, "force-reassign");

        OrderStatus status = dispatchService.rollbackRejectedForcedReassignment(
                replacement.getId(), second.getId(), "DRIVER_UNAVAILABLE", null, "reject-force-reassign");

        RideOrderEntity order = orderRepository.findByOrderNo(created.orderNo()).orElseThrow();
        assertThat(status).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(order.getCurrentDriverId()).isEqualTo(first.getId());
        assertThat(attemptRepository.findById(replacement.getId()).orElseThrow().getStatus())
                .isEqualTo(DispatchAttemptStatus.REJECTED);
    }

    @Test
    void passengerCannotCancelWhileForcedReassignmentWaitsForNewDriver() {
        DriverEntity first = driver("D111", "褚师傅", "13800000111", "QRD111");
        DriverEntity second = driver("D112", "卫师傅", "13800000112", "QRD112");
        PublicOrderService.CreateOrderResult created = publicOrderService.create(publicCommand());
        DispatchAttemptEntity firstAttempt = dispatchService.dispatch(created.orderNo(), first.getId(), 9001L, "first");
        String passengerToken = created.passengerAccessToken();
        dispatchService.accept(firstAttempt.getId(), first.getId(), "accept");

        dispatchService.forceReassign(created.orderNo(), second.getId(), "调度调整", 9001L, "force-reassign");

        assertThatThrownBy(() -> publicOrderService.cancel(created.orderNo(), passengerToken))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("ORDER_FORCED_REASSIGN_IN_PROGRESS"));
    }

    @Test
    void forcedReassignmentWaitingBlocksNormalReassign() {
        DriverEntity first = driver("D113", "魏师傅", "13800000113", "QRD113");
        DriverEntity second = driver("D114", "蒋师傅", "13800000114", "QRD114");
        DriverEntity third = driver("D115", "沈师傅", "13800000115", "QRD115");
        PublicOrderService.CreateOrderResult created = publicOrderService.create(publicCommand());
        DispatchAttemptEntity firstAttempt = dispatchService.dispatch(created.orderNo(), first.getId(), 9001L, "first");
        dispatchService.accept(firstAttempt.getId(), first.getId(), "accept");
        dispatchService.forceReassign(created.orderNo(), second.getId(), "调度调整", 9001L, "force-reassign");

        assertThatThrownBy(() -> dispatchService.reassignPending(
                created.orderNo(), third.getId(), 9001L, "normal-reassign", "reassign"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("ORDER_REASSIGN_BLOCKED_BY_FORCE_REASSIGN"));
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
