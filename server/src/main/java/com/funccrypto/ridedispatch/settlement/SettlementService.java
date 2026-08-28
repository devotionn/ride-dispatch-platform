package com.funccrypto.ridedispatch.settlement;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.funccrypto.ridedispatch.order.RideOrderEntity;
import com.funccrypto.ridedispatch.payment.PaymentEntity;
import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementService {

    private final DriverAccountRepository accountRepository;
    private final DriverLedgerRepository ledgerRepository;
    private final Clock clock;

    public SettlementService(DriverAccountRepository accountRepository, DriverLedgerRepository ledgerRepository, Clock clock) {
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.clock = clock;
    }

    @Transactional
    public DriverAccountEntity recordOnlinePaymentIncome(PaymentEntity payment, Long driverId, Long orderId) {
        if (driverId == null) throw new BusinessException("ORDER_DRIVER_REQUIRED", "订单没有当前司机");
        return recordIncome(
                driverId,
                orderId,
                "PAYMENT:" + payment.getPaymentNo() + ":SUCCESS",
                LedgerType.ONLINE_PAYMENT_INCOME,
                payment.getAmount(),
                payment.getAmount(),
                clock.instant());
    }

    @Transactional
    public DriverAccountEntity recordOfflineIncome(RideOrderEntity order) {
        if (order.getCurrentDriverId() == null) {
            throw new BusinessException("ORDER_DRIVER_REQUIRED", "订单没有当前司机");
        }
        if (order.getFinalAmount() == null || order.getFinalAmount() <= 0) {
            throw new BusinessException("ORDER_FINAL_AMOUNT_REQUIRED", "订单缺少有效最终金额");
        }
        return recordIncome(
                order.getCurrentDriverId(),
                order.getId(),
                "OFFLINE:" + order.getOrderNo() + ":COLLECTED",
                LedgerType.OFFLINE_INCOME,
                order.getFinalAmount(),
                0L,
                clock.instant());
    }

    @Transactional(readOnly = true)
    public DriverAccountEntity findAccount(Long driverId) {
        return accountRepository.findByDriverId(driverId)
                .orElseGet(() -> DriverAccountEntity.empty(driverId, clock.instant()));
    }

    private DriverAccountEntity recordIncome(Long driverId, Long orderId, String eventKey, LedgerType ledgerType,
            long businessIncomeAmount, long withdrawableDelta, Instant now) {
        var existing = ledgerRepository.findByEventKey(eventKey);
        if (existing.isPresent()) {
            return accountRepository.findByDriverId(driverId).orElseThrow();
        }
        DriverAccountEntity account = accountRepository.findByDriverIdForUpdate(driverId)
                .orElseGet(() -> accountRepository.save(DriverAccountEntity.empty(driverId, now)));
        long availableBefore = account.getAvailableBalance();
        long frozenBefore = account.getFrozenBalance();
        account.applyIncome(eventKey, businessIncomeAmount, withdrawableDelta, now);
        accountRepository.save(account);
        ledgerRepository.save(new DriverLedgerEntity(
                nextLedgerNo(), driverId, orderId, null, ledgerType,
                businessIncomeAmount, availableBefore, account.getAvailableBalance(),
                frozenBefore, account.getFrozenBalance(), businessIncomeAmount, withdrawableDelta,
                eventKey, now));
        return account;
    }

    private String nextLedgerNo() {
        return "LED" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
    }
}
