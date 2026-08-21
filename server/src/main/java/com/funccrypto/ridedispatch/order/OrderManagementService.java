package com.funccrypto.ridedispatch.order;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.funccrypto.ridedispatch.audit.AuditService;
import com.funccrypto.ridedispatch.dispatch.DispatchAttemptEntity;
import com.funccrypto.ridedispatch.dispatch.DispatchAttemptRepository;
import com.funccrypto.ridedispatch.dispatch.DispatchAttemptStatus;
import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
    private final Clock clock;

    public OrderManagementService(
            RideOrderRepository orderRepository,
            DispatchAttemptRepository attemptRepository,
            OrderProgressEventRepository progressRepository,
            PassengerAccessTokenService tokenService,
            AuditService auditService,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.attemptRepository = attemptRepository;
        this.progressRepository = progressRepository;
        this.tokenService = tokenService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public AdminCreateResult createByAdmin(AdminCreateCommand command, Long operatorId, String requestId) {
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
                progressRepository.findByOrderIdOrderByOccurredAtAsc(order.getId()));
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
            List<OrderProgressEventEntity> progressEvents) {
    }

    public record DriverPendingDispatch(DispatchAttemptEntity attempt, RideOrderEntity order) {
    }
}
