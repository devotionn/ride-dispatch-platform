package com.funccrypto.ridedispatch.settlement.api;

import java.util.List;

import com.funccrypto.ridedispatch.auth.AuthenticatedPrincipal;
import com.funccrypto.ridedispatch.settlement.WithdrawalEntity;
import com.funccrypto.ridedispatch.settlement.WithdrawalRepository;
import com.funccrypto.ridedispatch.settlement.WithdrawalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/withdrawals")
@PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
public class AdminSettlementController {

    private final WithdrawalRepository withdrawalRepository;
    private final WithdrawalService withdrawalService;

    public AdminSettlementController(WithdrawalRepository withdrawalRepository, WithdrawalService withdrawalService) {
        this.withdrawalRepository = withdrawalRepository;
        this.withdrawalService = withdrawalService;
    }

    @GetMapping
    List<DriverSettlementController.WithdrawalResponse> list() {
        return withdrawalRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(DriverSettlementController.WithdrawalResponse::from).toList();
    }

    @PostMapping("/{withdrawalNo}/approve")
    DriverSettlementController.WithdrawalResponse approve(@PathVariable String withdrawalNo, Authentication authentication) {
        return DriverSettlementController.WithdrawalResponse.from(
                withdrawalService.approve(withdrawalNo, operatorId(authentication)));
    }

    @PostMapping("/{withdrawalNo}/reject")
    DriverSettlementController.WithdrawalResponse reject(@PathVariable String withdrawalNo,
            @Valid @RequestBody RejectRequest request, Authentication authentication) {
        return DriverSettlementController.WithdrawalResponse.from(
                withdrawalService.reject(withdrawalNo, operatorId(authentication), request.reason()));
    }

    @PostMapping("/{withdrawalNo}/mark-paid")
    DriverSettlementController.WithdrawalResponse markPaid(@PathVariable String withdrawalNo, Authentication authentication) {
        return DriverSettlementController.WithdrawalResponse.from(
                withdrawalService.markPaid(withdrawalNo, operatorId(authentication)));
    }

    private Long operatorId(Authentication authentication) {
        return ((AuthenticatedPrincipal) authentication.getPrincipal()).principalId();
    }

    public record RejectRequest(@NotBlank String reason) {}
}
