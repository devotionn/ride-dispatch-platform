package com.funccrypto.ridedispatch.settlement.api;

import java.util.List;

import com.funccrypto.ridedispatch.auth.AuthenticatedPrincipal;
import com.funccrypto.ridedispatch.settlement.DriverLedgerEntity;
import com.funccrypto.ridedispatch.settlement.SettlementService;
import com.funccrypto.ridedispatch.settlement.WithdrawalEntity;
import com.funccrypto.ridedispatch.settlement.WithdrawalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/driver/me")
public class DriverSettlementController {

    private final SettlementService settlementService;
    private final WithdrawalService withdrawalService;
    private final com.funccrypto.ridedispatch.settlement.DriverLedgerRepository ledgerRepository;
    private final com.funccrypto.ridedispatch.settlement.WithdrawalRepository withdrawalRepository;

    public DriverSettlementController(
            SettlementService settlementService,
            WithdrawalService withdrawalService,
            com.funccrypto.ridedispatch.settlement.DriverLedgerRepository ledgerRepository,
            com.funccrypto.ridedispatch.settlement.WithdrawalRepository withdrawalRepository) {
        this.settlementService = settlementService;
        this.withdrawalService = withdrawalService;
        this.ledgerRepository = ledgerRepository;
        this.withdrawalRepository = withdrawalRepository;
    }

    @GetMapping("/account")
    AccountResponse account(Authentication authentication) {
        Long driverId = driverId(authentication);
        var account = settlementService.findAccount(driverId);
        return new AccountResponse(account.getDriverId(), account.getBusinessIncome(), account.getAvailableBalance(), account.getFrozenBalance());
    }

    @GetMapping("/ledger")
    List<LedgerResponse> ledger(Authentication authentication) {
        return ledgerRepository.findByDriverIdOrderByCreatedAtDescIdDesc(driverId(authentication)).stream()
                .map(LedgerResponse::from).toList();
    }

    @GetMapping("/withdrawals")
    List<WithdrawalResponse> withdrawals(Authentication authentication) {
        return withdrawalRepository.findByDriverIdOrderByCreatedAtDesc(driverId(authentication)).stream()
                .map(WithdrawalResponse::from).toList();
    }

    @PostMapping("/withdrawals")
    WithdrawalResponse request(Authentication authentication, @Valid @RequestBody WithdrawalRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        String effectiveKey = idempotencyKey == null || idempotencyKey.isBlank() ? request.idempotencyKey() : idempotencyKey;
        return WithdrawalResponse.from(withdrawalService.request(
                driverId(authentication), request.amount(), request.channel(), request.account(), effectiveKey));
    }

    private Long driverId(Authentication authentication) {
        return ((AuthenticatedPrincipal) authentication.getPrincipal()).principalId();
    }

    public record AccountResponse(Long driverId, long businessIncome, long availableBalance, long frozenBalance) {}
    public record WithdrawalRequest(@Positive long amount, @NotBlank String channel, @NotBlank String account, String idempotencyKey) {}
    public record LedgerResponse(String ledgerNo, String ledgerType, long amount, long availableBefore,
            long availableAfter, long frozenBefore, long frozenAfter, long businessIncomeAmount,
            long withdrawableDelta, String eventKey, java.time.Instant createdAt) {
        static LedgerResponse from(DriverLedgerEntity item) {
            return new LedgerResponse(item.getLedgerNo(), item.getLedgerType().name(), item.getAmount(),
                    item.getAvailableBefore(), item.getAvailableAfter(), item.getFrozenBefore(), item.getFrozenAfter(),
                    item.getBusinessIncomeAmount(), item.getWithdrawableDelta(), item.getEventKey(), item.getCreatedAt());
        }
    }
    public record WithdrawalResponse(String withdrawalNo, long amount, String channel, String account,
            String status, String reason, java.time.Instant createdAt, java.time.Instant reviewedAt, java.time.Instant paidAt) {
        static WithdrawalResponse from(WithdrawalEntity item) {
            return new WithdrawalResponse(item.getWithdrawalNo(), item.getAmount(), item.getChannel(), item.getAccount(),
                    item.getStatus().name(), item.getReason(), item.getCreatedAt(), item.getReviewedAt(), item.getPaidAt());
        }
    }
}
