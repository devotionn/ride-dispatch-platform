package com.funccrypto.ridedispatch.payment;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.funccrypto.ridedispatch.order.OrderStatus;
import com.funccrypto.ridedispatch.order.RideOrderRepository;
import com.funccrypto.ridedispatch.order.RideOrderEntity;
import com.funccrypto.ridedispatch.settlement.SettlementService;
import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentAttemptRepository attemptRepository;
    private final PaymentTokenService tokenService;
    private final RideOrderRepository orderRepository;
    private final SettlementService settlementService;
    private final Clock clock;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentAttemptRepository attemptRepository,
            PaymentTokenService tokenService,
            RideOrderRepository orderRepository,
            SettlementService settlementService,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.attemptRepository = attemptRepository;
        this.tokenService = tokenService;
        this.orderRepository = orderRepository;
        this.settlementService = settlementService;
        this.clock = clock;
    }

    /** Called inside the final-amount transaction; failure rolls the order state back with it. */
    @Transactional
    public PaymentCreation createForOrder(RideOrderEntity order, Instant now) {
        if (order.getId() == null || order.getFinalAmount() == null) {
            throw new BusinessException("PAYMENT_ORDER_NOT_READY", "订单金额尚未持久化，不能创建支付单");
        }
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException("PAYMENT_ORDER_STATE_INVALID", "订单当前不允许创建支付单");
        }
        if (paymentRepository.findByOrderId(order.getId()).isPresent()) {
            throw new BusinessException("PAYMENT_ALREADY_EXISTS", "该订单已存在支付单");
        }
        var token = tokenService.generate();
        PaymentEntity payment = paymentRepository.save(PaymentEntity.pending(
                nextPaymentNo(), order.getFinalAmount(), order.getId(), token.hash(), now,
                now.plusSeconds(30 * 60L)));
        return new PaymentCreation(payment, token.raw());
    }

    @Transactional(readOnly = true)
    public Optional<PaymentEntity> findByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    @Transactional(readOnly = true)
    public PaymentEntity requireByToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BusinessException("PAYMENT_ACCESS_DENIED", "付款凭证不能为空");
        }
        PaymentEntity payment = paymentRepository.findByAccessTokenHash(tokenService.hashOf(rawToken))
                .orElseThrow(() -> new BusinessException("PAYMENT_ACCESS_DENIED", "付款凭证无效或已失效"));
        Instant now = clock.instant();
        if (payment.isExpired(now) && payment.getStatus() == PaymentStatus.PENDING) {
            throw new BusinessException("PAYMENT_EXPIRED", "付款凭证已过期");
        }
        return payment;
    }

    /** Rotates a raw token for an authenticated passenger view; the old raw token is never persisted. */
    @Transactional
    public String issueFreshToken(PaymentEntity payment, Instant now) {
        PaymentEntity managed = paymentRepository.findByIdForUpdate(payment.getId())
                .orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "支付单不存在"));
        if (managed.getStatus() != PaymentStatus.PENDING) return null;
        var token = tokenService.generate();
        managed.replaceAccessTokenHash(token.hash(), now);
        paymentRepository.save(managed);
        return token.raw();
    }

    @Transactional
    public PaymentAttemptEntity createAttempt(String rawToken, PaymentChannel channel, Instant now) {
        return createAttempt(rawToken, channel, UUID.randomUUID().toString(), now);
    }

    @Transactional
    public PaymentAttemptEntity createAttempt(String rawToken, PaymentChannel channel, String idempotencyKey, Instant now) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException("IDEMPOTENCY_KEY_REQUIRED", "支付尝试必须提供幂等键");
        }
        PaymentEntity paymentSnapshot = requireByToken(rawToken);
        PaymentEntity payment = paymentRepository.findByIdForUpdate(paymentSnapshot.getId())
                .orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "支付单不存在"));
        var existing = attemptRepository.findByIdempotencyKey(idempotencyKey.trim());
        if (existing.isPresent()) {
            PaymentAttemptEntity attempt = existing.get();
            if (!attempt.getPaymentId().equals(payment.getId()) || attempt.getChannel() != channel) {
                throw new BusinessException("IDEMPOTENCY_CONFLICT", "同一幂等键对应的支付尝试参数不一致");
            }
            return attempt;
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BusinessException("PAYMENT_ALREADY_SETTLED", "支付单已完成，不能重复发起支付");
        }
        if (channel == null || channel == PaymentChannel.OFFLINE) {
            throw new BusinessException("PAYMENT_CHANNEL_INVALID", "线上支付渠道不合法");
        }
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
        PaymentAttemptEntity attempt = PaymentAttemptEntity.create(
                "ATT" + suffix, payment.getId(), idempotencyKey.trim(), channel, "MCH" + suffix, payment.getAmount(), now);
        attempt.markProcessing(now);
        return attemptRepository.save(attempt);
    }

    @Transactional
    public PaymentAttemptEntity succeedMockAttempt(String attemptNo, String thirdPartyTransactionNo,
            Long callbackAmount, Instant now) {
        PaymentAttemptEntity attempt = attemptRepository.findByAttemptNoForUpdate(attemptNo)
                .orElseThrow(() -> new BusinessException("PAYMENT_ATTEMPT_NOT_FOUND", "支付尝试不存在"));
        PaymentEntity payment = paymentRepository.findByIdForUpdate(attempt.getPaymentId())
                .orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "支付单不存在"));

        if (thirdPartyTransactionNo == null || thirdPartyTransactionNo.isBlank()) {
            thirdPartyTransactionNo = "MOCK-TX-" + attemptNo;
        }
        var sameTransaction = attemptRepository.findByThirdPartyTransactionNo(thirdPartyTransactionNo);
        if (sameTransaction.isPresent() && !sameTransaction.get().getAttemptNo().equals(attemptNo)) {
            throw new BusinessException("PAYMENT_TRANSACTION_DUPLICATE", "第三方支付流水号已被使用");
        }
        if (attempt.getStatus() == PaymentAttemptStatus.SUCCEEDED) {
            if (thirdPartyTransactionNo.equals(attempt.getThirdPartyTransactionNo())) return attempt;
            throw new BusinessException("PAYMENT_ATTEMPT_CALLBACK_CONFLICT", "支付回调流水号不一致");
        }
        if (payment.getStatus() == PaymentStatus.PAID) {
            attempt.markIgnoredAlreadySettled(now);
            return attemptRepository.save(attempt);
        }
        if (callbackAmount != null) payment.assertAmountMatches(callbackAmount);
        payment.assertAmountMatches(attempt.getAmount());
        attempt.markSucceeded(thirdPartyTransactionNo, null, now);
        payment.settle(attempt.getChannel(), thirdPartyTransactionNo, now);
        RideOrderEntity order = orderRepository.findByIdForUpdate(payment.getOrderId())
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
        order.completeFromPayment(payment.getSettlementMethod(), now);
        settlementService.recordOnlinePaymentIncome(payment, order.getCurrentDriverId(), order.getId());
        attemptRepository.save(attempt);
        return attempt;
    }

    @Transactional
    public PaymentAttemptEntity failAttempt(String attemptNo, Instant now) {
        PaymentAttemptEntity attempt = attemptRepository.findByAttemptNoForUpdate(attemptNo)
                .orElseThrow(() -> new BusinessException("PAYMENT_ATTEMPT_NOT_FOUND", "支付尝试不存在"));
        PaymentEntity payment = paymentRepository.findByIdForUpdate(attempt.getPaymentId())
                .orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "支付单不存在"));
        if (payment.getStatus() == PaymentStatus.PAID) {
            attempt.markIgnoredAlreadySettled(now);
        } else {
            attempt.markFailed(null, now);
        }
        return attemptRepository.save(attempt);
    }

    @Transactional
    public RideOrderEntity confirmOffline(String orderNo, Long driverId, String confirmation) {
        if (!"CONFIRM".equalsIgnoreCase(confirmation == null ? "" : confirmation.trim())) {
            throw new BusinessException("OFFLINE_CONFIRMATION_REQUIRED", "请先完成线下收款二次确认");
        }
        RideOrderEntity order = orderRepository.findByOrderNoForUpdate(orderNo)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
        if (order.getCurrentDriverId() == null || !order.getCurrentDriverId().equals(driverId)) {
            throw new BusinessException("ORDER_NOT_CURRENT_DRIVER", "当前订单不属于该司机");
        }
        PaymentEntity payment = paymentRepository.findByOrderId(order.getId())
                .orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "订单付款上下文不存在"));
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new BusinessException("PAYMENT_ALREADY_SETTLED", "该订单已经完成收款");
        }
        Instant now = clock.instant();
        payment.settleOffline(now);
        order.completeFromPayment(PaymentChannel.OFFLINE.name(), now);
        settlementService.recordOfflineIncome(order);
        paymentRepository.save(payment);
        return order;
    }

    @Transactional(readOnly = true)
    public PaymentView viewByToken(String rawToken) {
        PaymentEntity payment = requireByToken(rawToken);
        return new PaymentView(payment.getPaymentNo(), payment.getOrderId(), payment.getAmount(), payment.getStatus(),
                payment.getSettlementMethod(), attemptRepository.findByPaymentIdOrderByCreatedAtAsc(payment.getId()));
    }

    public record PaymentView(String paymentNo, Long orderId, long amount, PaymentStatus status,
            String settlementMethod, java.util.List<PaymentAttemptEntity> attempts) {}

    public record PaymentCreation(PaymentEntity payment, String rawToken) {}

    private String nextPaymentNo() {
        return "PAY" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
    }
}
