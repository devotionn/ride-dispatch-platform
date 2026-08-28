package com.funccrypto.ridedispatch.payment.api;

import com.funccrypto.ridedispatch.auth.AuthenticatedPrincipal;
import com.funccrypto.ridedispatch.order.OrderStatus;
import com.funccrypto.ridedispatch.payment.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/driver/orders")
public class OfflinePaymentController {

    private final PaymentService paymentService;

    public OfflinePaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/{orderNo}/offline-payment/confirm")
    OfflinePaymentResponse confirm(
            @PathVariable String orderNo,
            @Valid @RequestBody OfflineConfirmRequest request,
            Authentication authentication) {
        Long driverId = ((AuthenticatedPrincipal) authentication.getPrincipal()).principalId();
        var order = paymentService.confirmOffline(orderNo, driverId, request.confirmation());
        return new OfflinePaymentResponse(order.getOrderNo(), order.getStatus(), order.getSettlementMethod(), order.getCompletedAt());
    }

    public record OfflineConfirmRequest(@NotBlank String confirmation) {}

    public record OfflinePaymentResponse(String orderNo, OrderStatus status, String settlementMethod,
            java.time.Instant completedAt) {}
}
