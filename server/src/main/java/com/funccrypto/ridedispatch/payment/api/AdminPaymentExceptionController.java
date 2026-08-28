package com.funccrypto.ridedispatch.payment.api;

import java.time.Instant;
import java.util.List;

import com.funccrypto.ridedispatch.auth.AuthenticatedPrincipal;
import com.funccrypto.ridedispatch.payment.PaymentExceptionEntity;
import com.funccrypto.ridedispatch.payment.PaymentExceptionService;
import com.funccrypto.ridedispatch.payment.PaymentExceptionStatus;
import com.funccrypto.ridedispatch.payment.PaymentRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/payment-exceptions")
@PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
public class AdminPaymentExceptionController {

    private final com.funccrypto.ridedispatch.payment.PaymentExceptionRepository repository;
    private final PaymentRepository paymentRepository;
    private final PaymentExceptionService service;

    public AdminPaymentExceptionController(
            com.funccrypto.ridedispatch.payment.PaymentExceptionRepository repository,
            PaymentRepository paymentRepository,
            PaymentExceptionService service) {
        this.repository = repository;
        this.paymentRepository = paymentRepository;
        this.service = service;
    }

    @GetMapping
    List<ExceptionResponse> list() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    @PostMapping
    ExceptionResponse open(@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 80) String idempotencyKey,
            @Valid @RequestBody OpenRequest request, Authentication authentication) {
        return toResponse(service.open(idempotencyKey, request.paymentNo(), request.requestedAmount(), request.reason(), operatorId(authentication)));
    }

    @PostMapping("/{exceptionNo}/resolve")
    ExceptionResponse resolve(@PathVariable String exceptionNo, @Valid @RequestBody ResolveRequest request,
            Authentication authentication) {
        return toResponse(service.resolve(exceptionNo, request.externalRefundRef(), request.note(), operatorId(authentication)));
    }

    @PostMapping("/{exceptionNo}/reject")
    ExceptionResponse reject(@PathVariable String exceptionNo, @Valid @RequestBody RejectRequest request,
            Authentication authentication) {
        return toResponse(service.reject(exceptionNo, request.note(), operatorId(authentication)));
    }

    private ExceptionResponse toResponse(PaymentExceptionEntity item) {
        String paymentNo = paymentRepository.findById(item.getPaymentId()).map(payment -> payment.getPaymentNo()).orElse(null);
        return ExceptionResponse.from(item, paymentNo);
    }

    private Long operatorId(Authentication authentication) {
        return ((AuthenticatedPrincipal) authentication.getPrincipal()).principalId();
    }

    public record OpenRequest(@NotBlank String paymentNo, @Positive long requestedAmount,
            @NotBlank @Size(max = 500) String reason) {}
    public record ResolveRequest(@NotBlank @Size(max = 120) String externalRefundRef,
            @NotBlank @Size(max = 500) String note) {}
    public record RejectRequest(@NotBlank @Size(max = 500) String note) {}

    public record ExceptionResponse(String exceptionNo, String paymentNo, Long paymentId, Long orderId, long requestedAmount,
            PaymentExceptionStatus status, String reason, String externalRefundRef, String resolutionNote,
            Long createdBy, Long resolvedBy, Instant createdAt, Instant resolvedAt, Instant updatedAt) {
        static ExceptionResponse from(PaymentExceptionEntity item, String paymentNo) {
            return new ExceptionResponse(item.getExceptionNo(), paymentNo, item.getPaymentId(), item.getOrderId(),
                    item.getRequestedAmount(), item.getStatus(), item.getReason(), item.getExternalRefundRef(),
                    item.getResolutionNote(), item.getCreatedBy(), item.getResolvedBy(), item.getCreatedAt(),
                    item.getResolvedAt(), item.getUpdatedAt());
        }
    }
}
