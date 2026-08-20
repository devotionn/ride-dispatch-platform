package com.funccrypto.ridedispatch.dispatch;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import com.funccrypto.ridedispatch.audit.AuditService;
import com.funccrypto.ridedispatch.driver.DriverEntity;
import com.funccrypto.ridedispatch.driver.DriverRepository;
import com.funccrypto.ridedispatch.order.OrderStatus;
import com.funccrypto.ridedispatch.order.RideOrderEntity;
import com.funccrypto.ridedispatch.order.RideOrderRepository;
import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DispatchService {

    private final RideOrderRepository orderRepository;
    private final DriverRepository driverRepository;
    private final DispatchAttemptRepository attemptRepository;
    private final AuditService auditService;
    private final Clock clock;

    public DispatchService(
            RideOrderRepository orderRepository,
            DriverRepository driverRepository,
            DispatchAttemptRepository attemptRepository,
            AuditService auditService,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.driverRepository = driverRepository;
        this.attemptRepository = attemptRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public DispatchAttemptEntity dispatch(String orderNo, Long driverId, Long operatorId, String requestId) {
        RideOrderEntity order = orderRepository.findByOrderNoForUpdate(orderNo)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
        if (order.getStatus() != OrderStatus.PENDING_DISPATCH) {
            throw new BusinessException("ORDER_STATE_CONFLICT", "当前订单不能执行首次派单");
        }

        DriverEntity driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new BusinessException("DRIVER_NOT_FOUND", "司机不存在"));
        if (!driver.canReceiveNewOrder(order.getPassengerCount())) {
            throw new BusinessException("DRIVER_NOT_AVAILABLE", "司机当前不可接此订单");
        }
        if (attemptRepository.findFirstByOrderIdAndStatusOrderByDispatchedAtDesc(
                order.getId(), DispatchAttemptStatus.WAITING).isPresent()) {
            throw new BusinessException("DISPATCH_ALREADY_WAITING", "订单已有待确认司机");
        }

        Instant now = clock.instant();
        DispatchAttemptEntity attempt = attemptRepository.save(new DispatchAttemptEntity(
                order.getId(), driverId, DispatchType.MANUAL, operatorId, now));
        order.markPendingDriverConfirmation(now);

        auditService.log(
                "ADMIN",
                operatorId,
                "ORDER",
                order.getOrderNo(),
                "ORDER_DISPATCHED",
                Map.of("status", OrderStatus.PENDING_DISPATCH),
                Map.of("status", order.getStatus(), "targetDriverId", driverId),
                null,
                requestId,
                now);
        return attempt;
    }

    @Transactional
    public OrderStatus accept(Long attemptId, Long driverId, String requestId) {
        DispatchAttemptEntity snapshot = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new BusinessException("DISPATCH_ATTEMPT_NOT_FOUND", "派单记录不存在"));
        RideOrderEntity order = orderRepository.findByIdForUpdate(snapshot.getOrderId())
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
        DispatchAttemptEntity attempt = attemptRepository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new BusinessException("DISPATCH_ATTEMPT_NOT_FOUND", "派单记录不存在"));

        Instant now = clock.instant();
        attempt.accept(driverId, now);
        order.accept(driverId, now);
        auditService.log(
                "DRIVER",
                driverId,
                "ORDER",
                order.getOrderNo(),
                "DISPATCH_ACCEPTED",
                Map.of("status", OrderStatus.PENDING_DRIVER_CONFIRM),
                Map.of("status", order.getStatus(), "driverId", driverId),
                null,
                requestId,
                now);
        return order.getStatus();
    }

    @Transactional
    public OrderStatus reject(
            Long attemptId,
            Long driverId,
            String reasonCode,
            String reasonText,
            String requestId) {
        DispatchAttemptEntity snapshot = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new BusinessException("DISPATCH_ATTEMPT_NOT_FOUND", "派单记录不存在"));
        RideOrderEntity order = orderRepository.findByIdForUpdate(snapshot.getOrderId())
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
        DispatchAttemptEntity attempt = attemptRepository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new BusinessException("DISPATCH_ATTEMPT_NOT_FOUND", "派单记录不存在"));

        Instant now = clock.instant();
        attempt.reject(driverId, reasonCode, reasonText, now);
        order.rejectedByTargetDriver(now);
        auditService.log(
                "DRIVER",
                driverId,
                "ORDER",
                order.getOrderNo(),
                "DISPATCH_REJECTED",
                Map.of("status", OrderStatus.PENDING_DRIVER_CONFIRM),
                Map.of("status", order.getStatus()),
                reasonCode == null ? reasonText : reasonCode,
                requestId,
                now);
        return order.getStatus();
    }
}
