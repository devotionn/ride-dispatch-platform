package com.funccrypto.ridedispatch.dispatch;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
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

    public DispatchService(RideOrderRepository orderRepository, DriverRepository driverRepository,
            DispatchAttemptRepository attemptRepository, AuditService auditService, Clock clock) {
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
        DriverEntity driver = requireAvailableDriver(driverId, order.getPassengerCount());
        if (attemptRepository.findFirstByOrderIdAndStatusOrderByDispatchedAtDesc(order.getId(), DispatchAttemptStatus.WAITING).isPresent()) {
            throw new BusinessException("DISPATCH_ALREADY_WAITING", "订单已有待确认司机");
        }
        Instant now = clock.instant();
        DispatchAttemptEntity attempt = attemptRepository.save(new DispatchAttemptEntity(
                order.getId(), driver.getId(), DispatchType.MANUAL, operatorId, now));
        order.markPendingDriverConfirmation(now);
        auditService.log("ADMIN", operatorId, "ORDER", order.getOrderNo(), "ORDER_DISPATCHED",
                Map.of("status", OrderStatus.PENDING_DISPATCH),
                Map.of("status", order.getStatus(), "targetDriverId", driverId), null, requestId, now);
        return attempt;
    }

    @Transactional
    public DispatchAttemptEntity reassignPending(
            String orderNo, Long newDriverId, Long operatorId, String reason, String requestId) {
        RideOrderEntity order = orderRepository.findByOrderNoForUpdate(orderNo)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
        if (order.getStatus() != OrderStatus.PENDING_DRIVER_CONFIRM) {
            throw new BusinessException("ORDER_REASSIGN_REQUIRES_PENDING_CONFIRM", "仅待司机确认订单可直接改派");
        }
        if (order.getCurrentDriverId() != null) {
            throw new BusinessException("ORDER_REASSIGN_BLOCKED_BY_FORCE_REASSIGN", "强制改派确认中，不能普通改派");
        }

        DispatchAttemptEntity waitingSnapshot = attemptRepository
                .findFirstByOrderIdAndStatusOrderByDispatchedAtDesc(order.getId(), DispatchAttemptStatus.WAITING)
                .orElseThrow(() -> new BusinessException("DISPATCH_ATTEMPT_NOT_FOUND", "订单没有有效待确认派单"));
        DispatchAttemptEntity waiting = attemptRepository.findByIdForUpdate(waitingSnapshot.getId())
                .orElseThrow(() -> new BusinessException("DISPATCH_ATTEMPT_NOT_FOUND", "派单记录不存在"));
        if (waiting.getTargetDriverId().equals(newDriverId)) {
            throw new BusinessException("REASSIGN_SAME_DRIVER", "改派司机不能与当前待确认司机相同");
        }

        DriverEntity newDriver = requireAvailableDriver(newDriverId, order.getPassengerCount());
        Instant now = clock.instant();
        Long previousDriverId = waiting.getTargetDriverId();
        waiting.invalidateByReassign(now);
        order.returnToPendingDispatchForReassign(now);
        DispatchAttemptEntity replacement = attemptRepository.save(new DispatchAttemptEntity(
                order.getId(), newDriver.getId(), DispatchType.REASSIGN, operatorId, now, previousDriverId, reason));
        order.markPendingDriverConfirmation(now);

        auditService.log("ADMIN", operatorId, "ORDER", order.getOrderNo(), "ORDER_REASSIGNED_PENDING_CONFIRM",
                Map.of("status", OrderStatus.PENDING_DRIVER_CONFIRM, "targetDriverId", previousDriverId),
                Map.of("status", order.getStatus(), "targetDriverId", newDriverId), reason, requestId, now);
        return replacement;
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
        auditService.log("DRIVER", driverId, "ORDER", order.getOrderNo(), "DISPATCH_ACCEPTED",
                Map.of("status", OrderStatus.PENDING_DRIVER_CONFIRM),
                Map.of("status", order.getStatus(), "driverId", driverId), null, requestId, now);
        return order.getStatus();
    }

    @Transactional
    public OrderStatus reject(Long attemptId, Long driverId, String reasonCode, String reasonText, String requestId) {
        DispatchAttemptEntity snapshot = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new BusinessException("DISPATCH_ATTEMPT_NOT_FOUND", "派单记录不存在"));
        RideOrderEntity order = orderRepository.findByIdForUpdate(snapshot.getOrderId())
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
        DispatchAttemptEntity attempt = attemptRepository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new BusinessException("DISPATCH_ATTEMPT_NOT_FOUND", "派单记录不存在"));
        Instant now = clock.instant();
        attempt.reject(driverId, reasonCode, reasonText, now);
        order.rejectedByTargetDriver(now);
        auditService.log("DRIVER", driverId, "ORDER", order.getOrderNo(), "DISPATCH_REJECTED",
                Map.of("status", OrderStatus.PENDING_DRIVER_CONFIRM), Map.of("status", order.getStatus()),
                reasonCode == null ? reasonText : reasonCode, requestId, now);
        return order.getStatus();
    }

    @Transactional
    public OrderStatus forceCancel(
            String orderNo, String reason, Long operatorId, String requestId) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("FORCE_ACTION_REASON_REQUIRED", "强制操作必须填写原因");
        }
        RideOrderEntity order = orderRepository.findByOrderNoForUpdate(orderNo)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
        OrderStatus beforeStatus = order.getStatus();
        Long beforeDriverId = order.getCurrentDriverId();
        Instant now = clock.instant();
        order.forceCancelAfterAcceptance(now);
        invalidateWaitingAttempt(order, now);
        auditService.log("ADMIN", operatorId, "ORDER", orderNo, "ORDER_FORCE_CANCELLED",
                Map.of("status", beforeStatus.name(), "currentDriverId", String.valueOf(beforeDriverId)),
                new LinkedHashMap<>(Map.of("status", order.getStatus().name())) {{ put("currentDriverId", null); }}, reason, requestId, now);
        return order.getStatus();
    }

    @Transactional
    public DispatchAttemptEntity forceReassign(
            String orderNo, Long newDriverId, String reason, Long operatorId, String requestId) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("FORCE_ACTION_REASON_REQUIRED", "强制操作必须填写原因");
        }
        RideOrderEntity order = orderRepository.findByOrderNoForUpdate(orderNo)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
        DriverEntity newDriver = requireAvailableDriver(newDriverId, order.getPassengerCount());
        OrderStatus beforeStatus = order.getStatus();
        Long previousDriverId = order.getCurrentDriverId();
        if (previousDriverId == null || previousDriverId.equals(newDriver.getId())) {
            throw new BusinessException("ORDER_FORCE_REASSIGN_SAME_DRIVER", "改派司机不能与当前责任司机相同");
        }

        Instant now = clock.instant();
        order.beginForceReassignment(newDriver.getId(), now);
        DispatchAttemptEntity replacement = attemptRepository.save(new DispatchAttemptEntity(
                order.getId(), newDriver.getId(), DispatchType.FORCE_REASSIGN,
                operatorId, now, previousDriverId, reason));

        auditService.log("ADMIN", operatorId, "ORDER", orderNo, "ORDER_FORCE_REASSIGN_STARTED",
                Map.of("status", beforeStatus.name(), "currentDriverId", previousDriverId),
                Map.of("status", order.getStatus().name(), "pendingDriverId", newDriver.getId(),
                        "responsibleDriverId", previousDriverId),
                reason, requestId, now);
        return replacement;
    }

    @Transactional
    public OrderStatus rollbackRejectedForcedReassignment(
            Long attemptId, Long driverId, String reasonCode, String reasonText, String requestId) {
        DispatchAttemptEntity snapshot = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new BusinessException("DISPATCH_ATTEMPT_NOT_FOUND", "派单记录不存在"));
        RideOrderEntity order = orderRepository.findByIdForUpdate(snapshot.getOrderId())
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
        DispatchAttemptEntity attempt = attemptRepository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new BusinessException("DISPATCH_ATTEMPT_NOT_FOUND", "派单记录不存在"));
        if (attempt.getDispatchType() != DispatchType.FORCE_REASSIGN) {
            return reject(snapshot.getId(), driverId, reasonCode, reasonText, requestId);
        }
        Instant now = clock.instant();
        attempt.reject(driverId, reasonCode, reasonText, now);
        Long previousDriverId = attempt.getReassignFromDriverId();
        if (previousDriverId == null || !previousDriverId.equals(order.getCurrentDriverId())) {
            throw new BusinessException("ORDER_FORCE_REASSIGN_STATE_CONFLICT", "强制改派状态已变化，请刷新后重试");
        }
        order.restoreForceReassignment(previousDriverId, now);
        auditService.log("DRIVER", driverId, "ORDER", order.getOrderNo(), "ORDER_FORCE_REASSIGN_REJECTED",
                Map.of("status", OrderStatus.PENDING_DRIVER_CONFIRM.name(),
                        "pendingDriverId", driverId, "responsibleDriverId", previousDriverId),
                Map.of("status", order.getStatus().name(), "currentDriverId", previousDriverId),
                reasonCode == null ? reasonText : reasonCode, requestId, now);
        return order.getStatus();
    }
    private void invalidateWaitingAttempt(RideOrderEntity order, Instant now) {
        attemptRepository.findFirstByOrderIdAndStatusOrderByDispatchedAtDesc(
                        order.getId(), DispatchAttemptStatus.WAITING)
                .ifPresent(snapshot -> attemptRepository.findByIdForUpdate(snapshot.getId())
                        .ifPresent(attempt -> attempt.invalidateByOrder(now)));
    }
    private DriverEntity requireAvailableDriver(Long driverId, int passengerCount) {
        DriverEntity driver = driverRepository.findById(driverId)
                .orElseThrow(() -> new BusinessException("DRIVER_NOT_FOUND", "司机不存在"));
        if (!driver.canReceiveNewOrder(passengerCount)) {
            throw new BusinessException("DRIVER_NOT_AVAILABLE", "司机当前不可接此订单");
        }
        return driver;
    }
}
