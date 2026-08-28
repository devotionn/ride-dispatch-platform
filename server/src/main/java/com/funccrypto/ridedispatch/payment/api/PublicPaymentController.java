package com.funccrypto.ridedispatch.payment.api;

import java.time.Instant;
import java.util.List;

import com.funccrypto.ridedispatch.payment.PaymentAttemptEntity;
import com.funccrypto.ridedispatch.payment.PaymentChannel;
import com.funccrypto.ridedispatch.payment.PaymentService;
import com.funccrypto.ridedispatch.payment.PaymentStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/payments")
public class PublicPaymentController {

    private final PaymentService paymentService;

    public PublicPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/{paymentToken}")
    PaymentResponse get(@PathVariable String paymentToken) {
        return PaymentResponse.from(paymentService.viewByToken(paymentToken));
    }

    @GetMapping("/{paymentToken}/status")
    PaymentResponse status(@PathVariable String paymentToken) {
        return PaymentResponse.from(paymentService.viewByToken(paymentToken));
    }

    @PostMapping("/{paymentToken}/attempts")
    PaymentAttemptResponse createAttempt(
            @PathVariable String paymentToken,
            @Valid @RequestBody CreateAttemptRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String effectiveKey = idempotencyKey == null || idempotencyKey.isBlank() ? request.idempotencyKey() : idempotencyKey;
        return PaymentAttemptResponse.from(paymentService.createAttempt(paymentToken, request.channel(), effectiveKey, Instant.now()));
    }

    public record CreateAttemptRequest(@NotNull PaymentChannel channel, String idempotencyKey) {}

    public record PaymentResponse(
            String paymentNo,
            Long orderId,
            long amount,
            PaymentStatus status,
            String settlementMethod,
            List<PaymentAttemptResponse> attempts) {
        static PaymentResponse from(PaymentService.PaymentView view) {
            return new PaymentResponse(view.paymentNo(), view.orderId(), view.amount(), view.status(),
                    view.settlementMethod(), view.attempts().stream().map(PaymentAttemptResponse::from).toList());
        }
    }

    public record PaymentAttemptResponse(
            String attemptNo,
            PaymentChannel channel,
            long amount,
            com.funccrypto.ridedispatch.payment.PaymentAttemptStatus status,
            String thirdPartyTransactionNo,
            Instant createdAt,
            Instant paidAt) {
        static PaymentAttemptResponse from(PaymentAttemptEntity attempt) {
            return new PaymentAttemptResponse(attempt.getAttemptNo(), attempt.getChannel(), attempt.getAmount(),
                    attempt.getStatus(), attempt.getThirdPartyTransactionNo(), attempt.getCreatedAt(), attempt.getPaidAt());
        }
    }
}
