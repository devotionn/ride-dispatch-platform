package com.funccrypto.ridedispatch.payment;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.funccrypto.ridedispatch.audit.AuditService;
import com.funccrypto.ridedispatch.order.RideOrderEntity;
import com.funccrypto.ridedispatch.order.RideOrderRepository;
import com.funccrypto.ridedispatch.settlement.DriverAccountEntity;
import com.funccrypto.ridedispatch.settlement.DriverAccountRepository;
import com.funccrypto.ridedispatch.settlement.DriverLedgerEntity;
import com.funccrypto.ridedispatch.settlement.DriverLedgerRepository;
import com.funccrypto.ridedispatch.settlement.LedgerType;
import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfflinePaymentAdjustmentService {

    private final PaymentRepository paymentRepository;
    private final RideOrderRepository orderRepository;
    private final OfflinePaymentAdjustmentRepository adjustmentRepository;
    private final DriverAccountRepository accountRepository;
    private final DriverLedgerRepository ledgerRepository;
    private final AuditService auditService;
    private final Clock clock;

    public OfflinePaymentAdjustmentService(PaymentRepository paymentRepository, RideOrderRepository orderRepository,
            OfflinePaymentAdjustmentRepository adjustmentRepository, DriverAccountRepository accountRepository,
            DriverLedgerRepository ledgerRepository, AuditService auditService, Clock clock) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public AdjustmentView adjust(String paymentNo, long deltaAmount, String reason, String idempotencyKey,
            Long operatorId, String requestId) {
        if (deltaAmount == 0) throw new BusinessException("OFFLINE_ADJUSTMENT_ZERO", "纠偏金额不能为 0");
        String normalizedReason = reason == null ? "" : reason.trim();
        String normalizedKey = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (normalizedReason.isBlank()) throw new BusinessException("OFFLINE_ADJUSTMENT_REASON_REQUIRED", "纠偏必须填写原因");
        if (normalizedKey.isBlank()) throw new BusinessException("IDEMPOTENCY_KEY_REQUIRED", "幂等键不能为空");
        var existing = adjustmentRepository.findByIdempotencyKey(normalizedKey);
        if (existing.isPresent()) {
            OfflinePaymentAdjustmentEntity item = existing.get();
            PaymentEntity existingPayment = paymentRepository.findById(item.getPaymentId()).orElseThrow();
            if (item.getDeltaAmount() != deltaAmount || !item.getReason().equals(normalizedReason)
                    || !existingPayment.getPaymentNo().equals(paymentNo)) {
                throw new BusinessException("IDEMPOTENCY_CONFLICT", "同一幂等键对应的纠偏参数不一致");
            }
            return AdjustmentView.from(item, existingPayment.getPaymentNo());
        }

        PaymentEntity payment = paymentRepository.findByPaymentNoForUpdate(paymentNo)
                .orElseThrow(() -> new BusinessException("PAYMENT_NOT_FOUND", "支付单不存在"));
        if (payment.getStatus() != PaymentStatus.PAID || !PaymentChannel.OFFLINE.name().equals(payment.getSettlementMethod())) {
            throw new BusinessException("OFFLINE_ADJUSTMENT_REQUIRES_PAID_OFFLINE", "仅已完成线下收款的支付单可纠偏");
        }
        RideOrderEntity order = orderRepository.findByIdForUpdate(payment.getOrderId())
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "订单不存在"));
        Long driverId = order.getCurrentDriverId();
        if (driverId == null) throw new BusinessException("ORDER_DRIVER_REQUIRED", "订单没有当前司机");

        Instant now = clock.instant();
        DriverAccountEntity account = accountRepository.findByDriverIdForUpdate(driverId)
                .orElseThrow(() -> new BusinessException("DRIVER_ACCOUNT_NOT_FOUND", "司机账本不存在"));
        long before = account.getBusinessIncome();
        account.applyBusinessAdjustment(deltaAmount, now);
        accountRepository.save(account);
        String adjustmentNo = "ADJ" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
        OfflinePaymentAdjustmentEntity item = adjustmentRepository.save(new OfflinePaymentAdjustmentEntity(
                adjustmentNo, payment.getId(), order.getId(), driverId, deltaAmount, normalizedReason, normalizedKey, operatorId, now));
        ledgerRepository.save(new DriverLedgerEntity(
                "LED" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase(), driverId,
                order.getId(), null, LedgerType.OFFLINE_ADJUSTMENT, deltaAmount,
                account.getAvailableBalance(), account.getAvailableBalance(), account.getFrozenBalance(), account.getFrozenBalance(),
                deltaAmount, 0, "OFFLINE_ADJUSTMENT:" + adjustmentNo, now));
        Map<String, Object> beforeSnapshot = new LinkedHashMap<>();
        beforeSnapshot.put("businessIncome", before);
        Map<String, Object> afterSnapshot = new LinkedHashMap<>();
        afterSnapshot.put("businessIncome", account.getBusinessIncome());
        afterSnapshot.put("deltaAmount", deltaAmount);
        auditService.log("ADMIN", operatorId, "PAYMENT", paymentNo, "OFFLINE_PAYMENT_ADJUSTED",
                beforeSnapshot, afterSnapshot, normalizedReason, requestId, now);
        return AdjustmentView.from(item, payment.getPaymentNo());
    }

    public record AdjustmentView(String adjustmentNo, String paymentNo, Long orderId, Long driverId, long deltaAmount,
            String reason, String idempotencyKey, Long createdBy, Instant createdAt) {
        static AdjustmentView from(OfflinePaymentAdjustmentEntity item, String paymentNo) {
            return new AdjustmentView(item.getAdjustmentNo(), paymentNo, item.getOrderId(), item.getDriverId(),
                    item.getDeltaAmount(), item.getReason(), item.getIdempotencyKey(), item.getCreatedBy(), item.getCreatedAt());
        }
    }
}
