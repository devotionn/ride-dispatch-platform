package com.funccrypto.ridedispatch.order;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;

import com.funccrypto.ridedispatch.audit.AuditService;
import com.funccrypto.ridedispatch.audit.OperationLogEntity;
import com.funccrypto.ridedispatch.audit.OperationLogRepository;
import com.funccrypto.ridedispatch.dispatch.DispatchAttemptEntity;
import com.funccrypto.ridedispatch.dispatch.DispatchAttemptRepository;
import com.funccrypto.ridedispatch.dispatch.DispatchAttemptStatus;
import com.funccrypto.ridedispatch.payment.PaymentService;
import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderManagementService {

    private static final List<OrderStatus> DRIVER_ACTIVE_STATUSES = List.of(
            OrderStatus.ACCEPTED,
            OrderStatus.IN_SERVICE,
            OrderStatus.PENDING_PAYMENT);

    private final RideOrderRepository orderRepository;
    private final DispatchAttemptRepository attemptRepository;
    private final OrderProgressEventRepository progressRepository;
    private final PassengerAccessTokenService tokenService;
    private final AuditService auditService;
    private final OperationLogRepository operationLogRepository;
    private final PaymentService paymentService;
    private final Clock clock;

    public OrderManagementService(
            RideOrderRepository orderRepository,
            DispatchAttemptRepository attemptRepository,
            OrderProgressEventRepository progressRepository,
            PassengerAccessTokenService tokenService,
            AuditService auditService,
            OperationLogRepository operationLogRepository,
            PaymentService paymentService,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.attemptRepository = attemptRepository;
        this.progressRepository = progressRepository;
        this.tokenService = tokenService;
        this.auditService = auditService;
        this.operationLogRepository = operationLogRepository;
        this.paymentService = paymentService;
        this.clock = clock;
    }

    @Transactional
    public AdminCreateResult createByAdmin(AdminCreateCommand command, Long operatorId, String requestId) {
        validateCoordinatePair(command.pickupLatitude(), command.pickupLongitude(), "上车点");
        validateCoordinatePair(command.destinationLatitude(), command.destinationLongitude(), "目的地");
        Instant now = clock.instant();
        PassengerAccessTokenService.GeneratedToken token = tokenService.generate();
        RideOrderEntity order = orderRepository.save(new RideOrderEntity(
                nextOrderNo(),
                OrderSourceType.ADMIN_CREATED,
                null,
                command.passengerMobile(),
                token.hash(),
                command.pickupAddress(),
                command.pickupLatitude(),
                command.pickupLongitude(),
                command.destinationAddress(),
                command.destinationLatitude(),
                command.destinationLongitude(),
                command.passengerCount(),
                command.departureAt(),
                command.remark(),
                OrderStatus.PENDING_DISPATCH,
                now));

        auditService.log(
                "ADMIN",
                operatorId,
                "ORDER",
                order.getOrderNo(),
                "ORDER_CREATED_BY_ADMIN",
                Map.of(),
                Map.of("status", order.getStatus(), "sourceType", order.getSourceType()),
                null,
                requestId,
                now);
        return new AdminCreateResult(order, token.raw());
    }

    @Transactional(readOnly = true)
    public Page<RideOrderEntity> list(OrderStatus status, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return status == null ? orderRepository.findAll(pageable) : orderRepository.findByStatus(status, pageable);
    }

    @Transactional(readOnly = true)
    public OrderDetail detail(String orderNo) {
        RideOrderEntity order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
        return new OrderDetail(
                order,
                attemptRepository.findByOrderIdOrderByDispatchedAtDesc(order.getId()),
                progressRepository.findByOrderIdOrderByOccurredAtAsc(order.getId()),
                operationLogRepository.findByObjectTypeAndObjectIdOrderByCreatedAtAscIdAsc("ORDER", order.getOrderNo()));
    }

    @Transactional(readOnly = true)
    public List<DriverPendingDispatch> pendingForDriver(Long driverId) {
        return attemptRepository.findByTargetDriverIdAndStatusOrderByDispatchedAtAsc(driverId, DispatchAttemptStatus.WAITING)
                .stream()
                .map(attempt -> new DriverPendingDispatch(
                        attempt,
                        orderRepository.findById(attempt.getOrderId())
                                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"))))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RideOrderEntity> activeForDriver(Long driverId) {
        return orderRepository.findByCurrentDriverIdAndStatusInOrderByDepartureAtAsc(driverId, DRIVER_ACTIVE_STATUSES);
    }

    @Transactional(readOnly = true)
    public List<RideOrderEntity> historyForDriver(Long driverId) {
        return orderRepository.findByCurrentDriverIdAndStatusInOrderByCreatedAtDesc(
                driverId, List.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED, OrderStatus.EXCEPTION));
    }

    @Transactional
    public OrderStatus cancelPendingByAdmin(String orderNo, String reason, Long operatorId, String requestId) {
        requireReason(reason);
        RideOrderEntity order = orderRepository.findByOrderNoForUpdate(orderNo)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
        OrderStatus beforeStatus = order.getStatus();
        Instant now = clock.instant();
        order.cancelByAdminBeforeAcceptance(now);
        invalidateWaitingAttempt(order, now);
        auditService.log("ADMIN", operatorId, "ORDER", orderNo, "ORDER_CANCELLED_BY_ADMIN",
                Map.of("status", beforeStatus.name()),
                Map.of("status", order.getStatus().name()), reason, requestId, now);
        return order.getStatus();
    }

    @Transactional
    public OrderStatus markException(String orderNo, String reason, Long operatorId, String requestId) {
        requireReason(reason);
        RideOrderEntity order = orderRepository.findByOrderNoForUpdate(orderNo)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
        OrderStatus beforeStatus = order.getStatus();
        Instant now = clock.instant();
        order.markException(now);
        invalidateWaitingAttempt(order, now);
        auditService.log("ADMIN", operatorId, "ORDER", orderNo, "ORDER_MARKED_EXCEPTION",
                Map.of("status", beforeStatus.name()),
                new LinkedHashMap<>(Map.of("status", order.getStatus().name())), reason, requestId, now);
        return order.getStatus();
    }

    private void invalidateWaitingAttempt(RideOrderEntity order, Instant now) {
        attemptRepository.findFirstByOrderIdAndStatusOrderByDispatchedAtDesc(
                        order.getId(), DispatchAttemptStatus.WAITING)
                .ifPresent(snapshot -> attemptRepository.findByIdForUpdate(snapshot.getId())
                        .ifPresent(attempt -> attempt.invalidateByOrder(now)));
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("ADMIN_ORDER_REASON_REQUIRED", "管理操作必须填写原因");
        }
    }

    private void validateCoordinatePair(BigDecimal latitude, BigDecimal longitude, String label) {
        if ((latitude == null) != (longitude == null)) {
            throw new BusinessException("COORDINATES_INCOMPLETE", label + "经纬度必须同时填写或同时留空", HttpStatus.BAD_REQUEST);
        }
        if (latitude != null && (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0)) {
            throw new BusinessException("LATITUDE_INVALID", label + "纬度必须在 -90 到 90 之间", HttpStatus.BAD_REQUEST);
        }
        if (longitude != null && (longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new BusinessException("LONGITUDE_INVALID", label + "经度必须在 -180 到 180 之间", HttpStatus.BAD_REQUEST);
        }
    }

    @Transactional
    public RideOrderEntity advanceTrip(String orderNo, Long driverId, TripStage nextStage, String requestId) {
        RideOrderEntity order = orderRepository.findByOrderNoForUpdate(orderNo)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
        OrderStatus beforeStatus = order.getStatus();
        TripStage beforeStage = order.getTripStage();
        Instant now = clock.instant();
        order.advanceTrip(driverId, nextStage, now);
        progressRepository.save(new OrderProgressEventEntity(order.getId(), driverId, nextStage, now));
        auditService.log(
                "DRIVER",
                driverId,
                "ORDER",
                order.getOrderNo(),
                "ORDER_PROGRESS_ADVANCED",
                Map.of("status", beforeStatus.name(), "tripStage", String.valueOf(beforeStage)),
                Map.of("status", order.getStatus().name(), "tripStage", order.getTripStage().name()),
                null,
                requestId,
                now);
        return order;
    }

    @Transactional
    public RideOrderEntity submitFinalAmount(String orderNo, Long driverId, long amount, String requestId) {
        RideOrderEntity order = orderRepository.findByOrderNoForUpdate(orderNo)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
        OrderStatus beforeStatus = order.getStatus();
        Instant now = clock.instant();
        order.submitFinalAmount(driverId, amount, now);
        paymentService.createForOrder(order, now);
        auditService.log(
                "DRIVER",
                driverId,
                "ORDER",
                order.getOrderNo(),
                "ORDER_FINAL_AMOUNT_SUBMITTED",
                Map.of("status", beforeStatus.name()),
                Map.of("status", order.getStatus().name(), "finalAmount", amount),
                null,
                requestId,
                now);
        return order;
    }

    private String nextOrderNo() {
        return "RD" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    }

    public record AdminCreateCommand(
            String pickupAddress,
            BigDecimal pickupLatitude,
            BigDecimal pickupLongitude,
            String destinationAddress,
            BigDecimal destinationLatitude,
            BigDecimal destinationLongitude,
            int passengerCount,
            Instant departureAt,
            String passengerMobile,
            String remark) {
    }

    public record AdminCreateResult(RideOrderEntity order, String passengerAccessToken) {
    }

    public record OrderDetail(
            RideOrderEntity order,
            List<DispatchAttemptEntity> dispatchAttempts,
            List<OrderProgressEventEntity> progressEvents,
            List<OperationLogEntity> operationLogs) {
    }

    public record DriverPendingDispatch(DispatchAttemptEntity attempt, RideOrderEntity order) {
    }
}
