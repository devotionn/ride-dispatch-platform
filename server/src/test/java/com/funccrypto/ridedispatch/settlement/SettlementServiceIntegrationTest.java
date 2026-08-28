package com.funccrypto.ridedispatch.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.junit.jupiter.api.Test;

/** Local settlement invariants: fen precision, offline no-withdrawable, and reserve safety. */
class SettlementServiceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-24T00:00:00Z");

    @Test
    void offlineIncomeDoesNotIncreaseWithdrawableBalance() {
        DriverAccountEntity account = DriverAccountEntity.empty(101L, NOW);

        account.applyIncome("OFFLINE:RDLOCAL001:COLLECTED", 120_000L, 0L, NOW.plusSeconds(1));

        assertThat(account.getBusinessIncome()).isEqualTo(120_000L);
        assertThat(account.getAvailableBalance()).isZero();
        assertThat(account.getFrozenBalance()).isZero();
    }

    @Test
    void withdrawalCannotReserveMoreThanAvailableAndCannotDriveNegativeBalance() {
        DriverAccountEntity account = DriverAccountEntity.empty(101L, NOW);
        account.applyIncome("PAYMENT:PAYLOCAL:SUCCESS", 120_000L, 120_000L, NOW.plusSeconds(1));

        account.reserveWithdrawal("WDLOCAL001", 100_000L, NOW.plusSeconds(2));

        assertThat(account.getAvailableBalance()).isEqualTo(20_000L);
        assertThat(account.getFrozenBalance()).isEqualTo(100_000L);
        assertThatThrownBy(() -> account.reserveWithdrawal("WDLOCAL002", 20_001L, NOW.plusSeconds(3)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("余额");
    }

    @Test
    void offlineAdjustmentChangesOnlyBusinessIncomeAndIsAppendOnlySafe() {
        DriverAccountEntity account = DriverAccountEntity.empty(101L, NOW);
        account.applyIncome("OFFLINE:RDLOCAL001:COLLECTED", 120_000L, 0L, NOW.plusSeconds(1));

        account.applyBusinessAdjustment(-5_000L, NOW.plusSeconds(2));

        assertThat(account.getBusinessIncome()).isEqualTo(115_000L);
        assertThat(account.getAvailableBalance()).isZero();
        assertThat(account.getFrozenBalance()).isZero();
        assertThatThrownBy(() -> account.applyBusinessAdjustment(-200_000L, NOW.plusSeconds(3)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能为负");
    }
}
