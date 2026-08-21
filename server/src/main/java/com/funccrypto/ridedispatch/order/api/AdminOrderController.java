package com.funccrypto.ridedispatch.order.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import com.funccrypto.ridedispatch.auth.AuthenticatedPrincipal;
import com.funccrypto.ridedispatch.dispatch.DispatchAttemptEntity;
import com.funccrypto.ridedispatch.dispatch.DispatchAttemptStatus;
import com.funccrypto.ridedispatch.dispatch.DispatchType;
import com.funccrypto.ridedispatch.order.OrderManagementService;
import com.funccrypto.ridedispatch.order.OrderProgressEventEntity;
import com.funccrypto.ridedispatch.order.OrderSourceType;
import com.funccrypto.ridedispatch.order.OrderStatus;
import com.funccrypto.ridedispatch.order.RideOrderEntity;
import com.funccrypto.ridedispatch.order.TripStage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/orders")
@PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
public class AdminOrderController {

    private final OrderManagementService service;

    public AdminOrderController(OrderManagementService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreateResponse create(
            @Valid @RequestBody CreateRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        var result = service.createByAdmin(
                new OrderManagementService.AdminCreateCommand(
                        request.pickup().address(),
                        request.pickup().latitude(),
                        request.pickup().longitude(),
                        request.destination().address(),
                        request.destination().latitude(),
                        request.destination().longitude(),
                        request.passengerCount(),
                        request.departureAt().toInstant(),
                        request.mobile(),
                        request.remark()),
                operatorId(authentication),
                requestId(servletRequest));
        return new CreateResponse(
                result.order().getOrderNo(),
                result.order().getStatus(),
                result.passengerAccessToken());
    }

    @GetMapping
    PagedOrders list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        Page<RideOrderEntity> result = service.list(status, page, size);
        return new PagedOrders(
                result.getContent().stream().map(OrderSummary::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @GetMapping("/{orderNo}")
    DetailResponse detail(@PathVariable String orderNo) {
        var detail = service.detail(orderNo);
        return new DetailResponse(
                OrderView.from(detail.order()),
                detail.dispatchAttempts().stream().map(DispatchView::from).toList(),
                detail.progressEvents().stream().map(ProgressView::from).toList());
    }

    private Long operatorId(Authentication authentication) {
        return ((AuthenticatedPrincipal) authentication.getPrincipal()).principalId();
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? null : value.toString();
    }

    public record CreateRequest(
            @NotNull @Valid GeoPointRequest pickup,
            @NotNull @Valid GeoPointRequest destination,
            @Min(1) @Max(20) int passengerCount,
            @NotNull OffsetDateTime departureAt,
            @NotBlank @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确") String mobile,
            @Size(max = 500) String remark) {
    }

    public record CreateResponse(String orderNo, OrderStatus status, String passengerAccessToken) {
    }

    public record PagedOrders(List<OrderSummary> content, int page, int size, long totalElements, int totalPages) {
    }

    public record OrderSummary(
            String orderNo,
            OrderSourceType sourceType,
            OrderStatus status,
            Long currentDriverId,
            String pickupAddress,
            String destinationAddress,
            int passengerCount,
            Instant departureAt,
            Instant createdAt) {
        static OrderSummary from(RideOrderEntity order) {
            return new OrderSummary(
                    order.getOrderNo(), order.getSourceType(), order.getStatus(), order.getCurrentDriverId(),
                    order.getPickupAddress(), order.getDestinationAddress(), order.getPassengerCount(),
                    order.getDepartureAt(), order.getCreatedAt());
        }
    }

    public record OrderView(
            String orderNo,
            OrderSourceType sourceType,
            Long sourceDriverId,
            Long currentDriverId,
            String passengerMobile,
            String pickupAddress,
            BigDecimal pickupLatitude,
            BigDecimal pickupLongitude,
            String destinationAddress,
            BigDecimal destinationLatitude,
            BigDecimal destinationLongitude,
            int passengerCount,
            Instant departureAt,
            String remark,
            OrderStatus status,
            TripStage tripStage,
            Long finalAmount,
            Instant acceptedAt,
            Instant serviceStartedAt,
            Instant arrivedDestinationAt,
            Instant cancelledAt,
            Instant createdAt,
            Instant updatedAt) {
        static OrderView from(RideOrderEntity order) {
            return new OrderView(
                    order.getOrderNo(), order.getSourceType(), order.getSourceDriverId(), order.getCurrentDriverId(),
                    order.getPassengerMobile(), order.getPickupAddress(), order.getPickupLatitude(), order.getPickupLongitude(),
                    order.getDestinationAddress(), order.getDestinationLatitude(), order.getDestinationLongitude(),
                    order.getPassengerCount(), order.getDepartureAt(), order.getRemark(), order.getStatus(), order.getTripStage(),
                    order.getFinalAmount(), order.getAcceptedAt(), order.getServiceStartedAt(), order.getArrivedDestinationAt(),
                    order.getCancelledAt(), order.getCreatedAt(), order.getUpdatedAt());
        }
    }

    public record DispatchView(
            Long attemptId,
            Long targetDriverId,
            DispatchType dispatchType,
            DispatchAttemptStatus status,
            Long dispatchedBy,
            Instant dispatchedAt,
            Instant respondedAt,
            String rejectReasonCode,
            String rejectReasonText) {
        static DispatchView from(DispatchAttemptEntity attempt) {
            return new DispatchView(
                    attempt.getId(), attempt.getTargetDriverId(), attempt.getDispatchType(), attempt.getStatus(),
                    attempt.getDispatchedBy(), attempt.getDispatchedAt(), attempt.getRespondedAt(),
                    attempt.getRejectReasonCode(), attempt.getRejectReasonText());
        }
    }

    public record ProgressView(Long id, Long driverId, TripStage stage, Instant occurredAt) {
        static ProgressView from(OrderProgressEventEntity event) {
            return new ProgressView(event.getId(), event.getDriverId(), event.getStage(), event.getOccurredAt());
        }
    }

    public record DetailResponse(OrderView order, List<DispatchView> dispatchAttempts, List<ProgressView> progressEvents) {
    }
}
