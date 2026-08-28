package com.funccrypto.ridedispatch.payment.api;

import java.time.Instant;

import com.funccrypto.ridedispatch.payment.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Profile("local")
@RestController
@RequestMapping("/api/v1/local/mock-payments")
public class LocalMockPaymentController {

    private final PaymentService paymentService;

    public LocalMockPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/{attemptNo}/success")
    PublicPaymentController.PaymentAttemptResponse success(
            @PathVariable String attemptNo,
            @Valid @RequestBody(required = false) MockCallbackRequest request) {
        String transactionNo = request == null ? null : request.thirdPartyTransactionNo();
        Long callbackAmount = request == null ? null : request.amount();
        return PublicPaymentController.PaymentAttemptResponse.from(
                paymentService.succeedMockAttempt(attemptNo, transactionNo, callbackAmount, Instant.now()));
    }

    @PostMapping("/{attemptNo}/failure")
    PublicPaymentController.PaymentAttemptResponse failure(@PathVariable String attemptNo) {
        return PublicPaymentController.PaymentAttemptResponse.from(paymentService.failAttempt(attemptNo, Instant.now()));
    }

    public record MockCallbackRequest(
            @Size(max = 100) String thirdPartyTransactionNo,
            @Positive Long amount) {}
}
