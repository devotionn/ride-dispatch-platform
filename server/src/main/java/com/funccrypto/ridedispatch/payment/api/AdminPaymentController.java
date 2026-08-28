package com.funccrypto.ridedispatch.payment.api;

import java.util.List;

import com.funccrypto.ridedispatch.auth.AuthenticatedPrincipal;
import com.funccrypto.ridedispatch.payment.PaymentAttemptEntity;
import com.funccrypto.ridedispatch.payment.PaymentEntity;
import com.funccrypto.ridedispatch.payment.OfflinePaymentAdjustmentService;
import com.funccrypto.ridedispatch.payment.PaymentRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/payments")
@PreAuthorize("hasAnyRole('ADMIN','DISPATCHER','FINANCE')")
public class AdminPaymentController {

    private final PaymentRepository paymentRepository;
    private final com.funccrypto.ridedispatch.payment.PaymentAttemptRepository attemptRepository;
    private final OfflinePaymentAdjustmentService adjustmentService;

    public AdminPaymentController(PaymentRepository paymentRepository,
            com.funccrypto.ridedispatch.payment.PaymentAttemptRepository attemptRepository,
            OfflinePaymentAdjustmentService adjustmentService) {
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
        this.adjustmentService = adjustmentService;
    }

    @GetMapping
    List<PaymentResponse> list() {
        return paymentRepository.findAllByOrderByCreatedAtDesc().stream().map(this::from).toList();
    }

    @GetMapping("/{paymentNo}")
    PaymentResponse detail(@PathVariable String paymentNo) {
        PaymentEntity payment = paymentRepository.findByPaymentNo(paymentNo)
                .orElseThrow(() -> new com.funccrypto.ridedispatch.shared.error.BusinessException("PAYMENT_NOT_FOUND", "支付单不存在"));
        return from(payment);
    }

    @PostMapping("/{paymentNo}/offline-adjustments")
    OfflinePaymentAdjustmentService.AdjustmentView adjustOffline(
            @PathVariable String paymentNo,
            @Valid @RequestBody OfflineAdjustmentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();
        Object requestId = servletRequest.getAttribute("requestId");
        String effectiveKey = idempotencyKey == null || idempotencyKey.isBlank() ? request.idempotencyKey() : idempotencyKey;
        return adjustmentService.adjust(paymentNo, request.deltaAmount(), request.reason(), effectiveKey,
                principal.principalId(), requestId == null ? null : requestId.toString());
    }

    private PaymentResponse from(PaymentEntity payment) {
        return new PaymentResponse(payment.getPaymentNo(), payment.getOrderId(), payment.getAmount(),
                payment.getStatus().name(), payment.getSettlementMethod(),
                attemptRepository.findByPaymentIdOrderByCreatedAtAsc(payment.getId()).stream().map(PaymentAttemptView::from).toList());
    }

    public record PaymentResponse(String paymentNo, Long orderId, long amount, String status,
            String settlementMethod, List<PaymentAttemptView> attempts) {}
    public record OfflineAdjustmentRequest(
            @NotNull Long deltaAmount,
            @NotBlank @Size(max = 500) String reason,
            @Size(max = 120) String idempotencyKey) {}
    public record PaymentAttemptView(String attemptNo, String channel, long amount, String status,
            String merchantOrderNo, String thirdPartyTransactionNo, java.time.Instant createdAt, java.time.Instant paidAt) {
        static PaymentAttemptView from(PaymentAttemptEntity item) {
            return new PaymentAttemptView(item.getAttemptNo(), item.getChannel().name(), item.getAmount(), item.getStatus().name(),
                    item.getMerchantOrderNo(), item.getThirdPartyTransactionNo(), item.getCreatedAt(), item.getPaidAt());
        }
    }
}
