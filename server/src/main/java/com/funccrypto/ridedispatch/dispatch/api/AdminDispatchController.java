package com.funccrypto.ridedispatch.dispatch.api;

import java.util.List;

import com.funccrypto.ridedispatch.auth.AuthenticatedPrincipal;
import com.funccrypto.ridedispatch.dispatch.DispatchAttemptEntity;
import com.funccrypto.ridedispatch.dispatch.DispatchAttemptStatus;
import com.funccrypto.ridedispatch.dispatch.DispatchService;
import com.funccrypto.ridedispatch.dispatch.NearbyDriverService;
import com.funccrypto.ridedispatch.dispatch.NearbyDriverView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/orders")
@PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
public class AdminDispatchController {

    private final NearbyDriverService nearbyDriverService;
    private final DispatchService dispatchService;

    public AdminDispatchController(NearbyDriverService nearbyDriverService, DispatchService dispatchService) {
        this.nearbyDriverService = nearbyDriverService;
        this.dispatchService = dispatchService;
    }

    @GetMapping("/{orderNo}/nearby-drivers")
    List<NearbyDriverView> nearby(@PathVariable String orderNo) {
        return nearbyDriverService.findNearby(orderNo);
    }

    @PostMapping("/{orderNo}/dispatch")
    DispatchResponse dispatch(
            @PathVariable String orderNo,
            @Valid @RequestBody DispatchRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();
        DispatchAttemptEntity attempt = dispatchService.dispatch(
                orderNo, request.driverId(), principal.principalId(), requestId(servletRequest));
        return new DispatchResponse(attempt.getId(), attempt.getTargetDriverId(), attempt.getStatus());
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? null : value.toString();
    }

    public record DispatchRequest(@NotNull Long driverId) {
    }

    public record DispatchResponse(Long attemptId, Long targetDriverId, DispatchAttemptStatus status) {
    }
}
