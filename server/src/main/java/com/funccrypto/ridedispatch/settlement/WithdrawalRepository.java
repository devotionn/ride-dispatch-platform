package com.funccrypto.ridedispatch.settlement;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WithdrawalRepository extends JpaRepository<WithdrawalEntity, Long> {
    Optional<WithdrawalEntity> findByWithdrawalNo(String withdrawalNo);
    Optional<WithdrawalEntity> findByIdempotencyKey(String idempotencyKey);
    List<WithdrawalEntity> findByDriverIdOrderByCreatedAtDesc(Long driverId);
    List<WithdrawalEntity> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WithdrawalEntity w where w.withdrawalNo = :withdrawalNo")
    Optional<WithdrawalEntity> findByWithdrawalNoForUpdate(@Param("withdrawalNo") String withdrawalNo);
}
