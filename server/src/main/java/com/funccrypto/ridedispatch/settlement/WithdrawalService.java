package com.funccrypto.ridedispatch.settlement;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawalService {

    private final DriverAccountRepository accountRepository;
    private final DriverLedgerRepository ledgerRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final Clock clock;

    public WithdrawalService(
            DriverAccountRepository accountRepository,
            DriverLedgerRepository ledgerRepository,
            WithdrawalRepository withdrawalRepository,
            Clock clock) {
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
        this.withdrawalRepository = withdrawalRepository;
        this.clock = clock;
    }

    @Transactional
    public WithdrawalEntity request(Long driverId, long amount, String channel, String accountValue) {
        return request(driverId, amount, channel, accountValue, java.util.UUID.randomUUID().toString());
    }

    @Transactional
    public WithdrawalEntity request(Long driverId, long amount, String channel, String accountValue, String idempotencyKey) {
        if (channel == null || channel.isBlank() || accountValue == null || accountValue.isBlank()) {
            throw new BusinessException("WITHDRAWAL_ACCOUNT_REQUIRED", "提现方式和收款账号不能为空");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException("IDEMPOTENCY_KEY_REQUIRED", "提现申请必须提供幂等键");
        }
        String normalizedKey = idempotencyKey.trim();
        var existing = withdrawalRepository.findByIdempotencyKey(normalizedKey);
        if (existing.isPresent()) {
            WithdrawalEntity item = existing.get();
            if (!item.getDriverId().equals(driverId) || item.getAmount() != amount
                    || !item.getChannel().equals(channel) || !item.getAccount().equals(accountValue)) {
                throw new BusinessException("IDEMPOTENCY_CONFLICT", "同一幂等键对应的提现参数不一致");
            }
            return item;
        }
        Instant now = clock.instant();
        DriverAccountEntity account = accountRepository.findByDriverIdForUpdate(driverId)
                .orElseThrow(() -> new BusinessException("DRIVER_ACCOUNT_NOT_FOUND", "司机账户不存在"));
        String withdrawalNo = nextWithdrawalNo();
        long availableBefore = account.getAvailableBalance();
        long frozenBefore = account.getFrozenBalance();
        account.reserveWithdrawal(withdrawalNo, amount, now);
        WithdrawalEntity withdrawal = withdrawalRepository.save(
                WithdrawalEntity.request(withdrawalNo, driverId, normalizedKey, amount, channel, accountValue, now));
        accountRepository.save(account);
        ledgerRepository.save(new DriverLedgerEntity(
                nextLedgerNo(), driverId, null, withdrawal.getId(), LedgerType.WITHDRAWAL_RESERVE,
                -amount, availableBefore, account.getAvailableBalance(), frozenBefore, account.getFrozenBalance(),
                0L, -amount, "WITHDRAWAL:" + withdrawalNo + ":RESERVE", now));
        return withdrawal;
    }

    @Transactional
    public WithdrawalEntity approve(String withdrawalNo, Long operatorId) {
        WithdrawalEntity withdrawal = lockWithdrawal(withdrawalNo);
        withdrawal.approve(operatorId, clock.instant());
        return withdrawal;
    }

    @Transactional
    public WithdrawalEntity reject(String withdrawalNo, Long operatorId, String reason) {
        WithdrawalEntity withdrawal = lockWithdrawal(withdrawalNo);
        Instant now = clock.instant();
        DriverAccountEntity account = accountRepository.findByDriverIdForUpdate(withdrawal.getDriverId())
                .orElseThrow(() -> new BusinessException("DRIVER_ACCOUNT_NOT_FOUND", "司机账户不存在"));
        long availableBefore = account.getAvailableBalance();
        long frozenBefore = account.getFrozenBalance();
        withdrawal.reject(operatorId, reason, now);
        account.releaseFrozen(withdrawal.getAmount(), now);
        withdrawalRepository.save(withdrawal);
        accountRepository.save(account);
        saveWithdrawalLedger(withdrawal, LedgerType.WITHDRAWAL_REJECT, withdrawal.getAmount(),
                availableBefore, account.getAvailableBalance(), frozenBefore, account.getFrozenBalance(),
                "WITHDRAWAL:" + withdrawalNo + ":REJECT", now);
        return withdrawal;
    }

    @Transactional
    public WithdrawalEntity markPaid(String withdrawalNo, Long operatorId) {
        WithdrawalEntity withdrawal = lockWithdrawal(withdrawalNo);
        Instant now = clock.instant();
        DriverAccountEntity account = accountRepository.findByDriverIdForUpdate(withdrawal.getDriverId())
                .orElseThrow(() -> new BusinessException("DRIVER_ACCOUNT_NOT_FOUND", "司机账户不存在"));
        long availableBefore = account.getAvailableBalance();
        long frozenBefore = account.getFrozenBalance();
        withdrawal.markPaid(operatorId, now);
        account.consumeFrozen(withdrawal.getAmount(), now);
        withdrawalRepository.save(withdrawal);
        accountRepository.save(account);
        saveWithdrawalLedger(withdrawal, LedgerType.WITHDRAWAL_PAID, withdrawal.getAmount(),
                availableBefore, account.getAvailableBalance(), frozenBefore, account.getFrozenBalance(),
                "WITHDRAWAL:" + withdrawalNo + ":PAID", now);
        return withdrawal;
    }

    private WithdrawalEntity lockWithdrawal(String withdrawalNo) {
        return withdrawalRepository.findByWithdrawalNoForUpdate(withdrawalNo)
                .orElseThrow(() -> new BusinessException("WITHDRAWAL_NOT_FOUND", "提现申请不存在"));
    }

    private void saveWithdrawalLedger(WithdrawalEntity withdrawal, LedgerType type, long amount,
            long availableBefore, long availableAfter, long frozenBefore, long frozenAfter,
            String eventKey, Instant now) {
        ledgerRepository.save(new DriverLedgerEntity(
                nextLedgerNo(), withdrawal.getDriverId(), null, withdrawal.getId(), type,
                amount, availableBefore, availableAfter, frozenBefore, frozenAfter,
                0L, availableAfter - availableBefore, eventKey, now));
    }

    private String nextWithdrawalNo() {
        return "WD" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
    }

    private String nextLedgerNo() {
        return "LED" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
    }
}
