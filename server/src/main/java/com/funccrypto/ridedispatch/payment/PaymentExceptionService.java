package com.funccrypto.ridedispatch.payment;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.funccrypto.ridedispatch.audit.AuditService;
import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentExceptionService {

    private final PaymentRepository paymentRepository;
    private final PaymentExceptionRepository exceptionRepository;
    private final AuditService auditService;
    private final Clock clock;

    public PaymentExceptionService(PaymentRepository paymentRepository, PaymentExceptionRepository exceptionRepository,
            AuditService auditService, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.exceptionRepository = exceptionRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public PaymentExceptionEntity open(String idempotencyKey, String paymentNo, long requestedAmount, String reason, Long operatorId) {
        PaymentEntity payment = paymentRepository.findByPaymentNoForUpdate(paymentNo)
                .orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "支付单不存在"));
        PaymentExceptionEntity replay = exceptionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (replay != null) {
            if (!replay.getPaymentId().equals(payment.getId()) || replay.getRequestedAmount() != requestedAmount
                    || !replay.getReason().equals(reason == null ? "" : reason.trim())) {
                throw new BusinessException("IDEMPOTENCY_CONFLICT", "同一幂等键对应的退款异常参数不一致");
            }
            return replay;
        }
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new BusinessException("PAYMENT_EXCEPTION_PAYMENT_NOT_PAID", "只有已支付订单可以登记退款异常");
        }
        if (requestedAmount <= 0 || requestedAmount > payment.getAmount()) {
            throw new BusinessException("PAYMENT_EXCEPTION_AMOUNT_INVALID", "退款异常金额必须在支付金额范围内");
        }
        long activeRequestedAmount = exceptionRepository.sumActiveRequestedAmount(payment.getId(), PaymentExceptionStatus.REJECTED);
        if (activeRequestedAmount > payment.getAmount() - requestedAmount) {
            throw new BusinessException("PAYMENT_EXCEPTION_TOTAL_AMOUNT_INVALID", "同一支付单的有效退款异常累计金额不能超过支付金额");
        }
        Instant now = clock.instant();
        PaymentExceptionEntity exception = exceptionRepository.save(PaymentExceptionEntity.open(
                nextExceptionNo(), idempotencyKey, payment.getId(), payment.getOrderId(), requestedAmount, reason, operatorId, now));
        auditService.log("ADMIN", operatorId, "PAYMENT_EXCEPTION", exception.getExceptionNo(),
                "PAYMENT_REFUND_EXCEPTION_OPENED", null, snapshot(exception), reason, null, now);
        return exception;
    }

    @Transactional
    public PaymentExceptionEntity resolve(String exceptionNo, String externalRef, String note, Long operatorId) {
        PaymentExceptionEntity exception = lock(exceptionNo);
        Snapshot before = snapshot(exception);
        Instant now = clock.instant();
        exception.resolve(operatorId, externalRef, note, now);
        exceptionRepository.save(exception);
        auditService.log("ADMIN", operatorId, "PAYMENT_EXCEPTION", exception.getExceptionNo(),
                "PAYMENT_REFUND_EXCEPTION_RESOLVED", before, snapshot(exception), note, null, now);
        return exception;
    }

    @Transactional
    public PaymentExceptionEntity reject(String exceptionNo, String note, Long operatorId) {
        PaymentExceptionEntity exception = lock(exceptionNo);
        Snapshot before = snapshot(exception);
        Instant now = clock.instant();
        exception.reject(operatorId, note, now);
        exceptionRepository.save(exception);
        auditService.log("ADMIN", operatorId, "PAYMENT_EXCEPTION", exception.getExceptionNo(),
                "PAYMENT_REFUND_EXCEPTION_REJECTED", before, snapshot(exception), note, null, now);
        return exception;
    }

    private PaymentExceptionEntity lock(String exceptionNo) {
        return exceptionRepository.findByExceptionNoForUpdate(exceptionNo)
                .orElseThrow(() -> new BusinessException("PAYMENT_EXCEPTION_NOT_FOUND", "退款异常不存在"));
    }

    private String nextExceptionNo() {
        return "RF" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
    }

    private Snapshot snapshot(PaymentExceptionEntity exception) {
        return new Snapshot(exception.getExceptionNo(), exception.getPaymentId(), exception.getOrderId(),
                exception.getRequestedAmount(), exception.getStatus().name(), exception.getExternalRefundRef(),
                exception.getResolutionNote());
    }

    private record Snapshot(String exceptionNo, Long paymentId, Long orderId, long requestedAmount,
            String status, String externalRefundRef, String resolutionNote) {}
}
