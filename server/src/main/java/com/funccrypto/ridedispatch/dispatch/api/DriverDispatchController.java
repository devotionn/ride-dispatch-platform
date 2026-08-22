package com.funccrypto.ridedispatch.dispatch.api;

import com.funccrypto.ridedispatch.auth.AuthenticatedPrincipal;
import com.funccrypto.ridedispatch.dispatch.DispatchService;
import com.funccrypto.ridedispatch.order.OrderStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/driver/dispatch-attempts")
public class DriverDispatchController {

    private final DispatchService dispatchService;

    public DriverDispatchController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @PostMapping("/{attemptId}/accept")
    OrderStatusResponse accept(
            @PathVariable Long attemptId,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        OrderStatus status = dispatchService.accept(
                attemptId, driverId(authentication), requestId(servletRequest));
        return new OrderStatusResponse(status);
    }

    @PostMapping("/{attemptId}/reject")
    OrderStatusResponse reject(
            @PathVariable Long attemptId,
            @Valid @RequestBody RejectRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        OrderStatus status = dispatchService.rollbackRejectedForcedReassignment(
                attemptId,
                driverId(authentication),
                request.reasonCode(),
                request.reasonText(),
                requestId(servletRequest));
        return new OrderStatusResponse(status);
    }

    private Long driverId(Authentication authentication) {
        return ((AuthenticatedPrincipal) authentication.getPrincipal()).principalId();
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? null : value.toString();
    }

    public record RejectRequest(
            @Size(max = 60) String reasonCode,
            @Size(max = 255) String reasonText) {
    }

    public record OrderStatusResponse(OrderStatus status) {
    }
}
