package com.funccrypto.ridedispatch.order.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.funccrypto.ridedispatch.auth.AuthenticatedPrincipal;
import com.funccrypto.ridedispatch.order.OrderManagementService;
import com.funccrypto.ridedispatch.order.OrderStatus;
import com.funccrypto.ridedispatch.order.RideOrderEntity;
import com.funccrypto.ridedispatch.order.TripStage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/driver/orders")
public class DriverOrderController {

    private final OrderManagementService service;

    public DriverOrderController(OrderManagementService service) {
        this.service = service;
    }

    @GetMapping("/pending-confirmation")
    List<PendingDispatchResponse> pending(Authentication authentication) {
        return service.pendingForDriver(driverId(authentication)).stream()
                .map(PendingDispatchResponse::from)
                .toList();
    }

    @GetMapping("/active")
    List<DriverOrderView> active(Authentication authentication) {
        return service.activeForDriver(driverId(authentication)).stream().map(DriverOrderView::from).toList();
    }

    @PostMapping("/{orderNo}/progress")
    ProgressResponse progress(
            @PathVariable String orderNo,
            @Valid @RequestBody ProgressRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        RideOrderEntity order = service.advanceTrip(
                orderNo, driverId(authentication), request.stage(), requestId(servletRequest));
        return ProgressResponse.from(order);
    }

    @PostMapping("/{orderNo}/final-amount")
    ProgressResponse finalAmount(
            @PathVariable String orderNo,
            @Valid @RequestBody FinalAmountRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        RideOrderEntity order = service.submitFinalAmount(
                orderNo, driverId(authentication), request.amount(), requestId(servletRequest));
        return ProgressResponse.from(order);
    }

    private Long driverId(Authentication authentication) {
        return ((AuthenticatedPrincipal) authentication.getPrincipal()).principalId();
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? null : value.toString();
    }

    public record ProgressRequest(@NotNull TripStage stage) {
    }

    public record FinalAmountRequest(@Min(1) long amount) {
    }

    public record PendingDispatchResponse(Long attemptId, Instant dispatchedAt, DriverOrderView order) {
        static PendingDispatchResponse from(OrderManagementService.DriverPendingDispatch item) {
            return new PendingDispatchResponse(
                    item.attempt().getId(), item.attempt().getDispatchedAt(), DriverOrderView.from(item.order()));
        }
    }

    public record DriverOrderView(
            String orderNo,
            OrderStatus status,
            TripStage tripStage,
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
            Long finalAmount) {
        static DriverOrderView from(RideOrderEntity order) {
            return new DriverOrderView(
                    order.getOrderNo(), order.getStatus(), order.getTripStage(), order.getPassengerMobile(),
                    order.getPickupAddress(), order.getPickupLatitude(), order.getPickupLongitude(),
                    order.getDestinationAddress(), order.getDestinationLatitude(), order.getDestinationLongitude(),
                    order.getPassengerCount(), order.getDepartureAt(), order.getRemark(), order.getFinalAmount());
        }
    }

    public record ProgressResponse(OrderStatus status, TripStage tripStage, Long finalAmount) {
        static ProgressResponse from(RideOrderEntity order) {
            return new ProgressResponse(order.getStatus(), order.getTripStage(), order.getFinalAmount());
        }
    }
}
