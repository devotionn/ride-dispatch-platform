package com.funccrypto.ridedispatch.payment;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttemptEntity, Long> {

    Optional<PaymentAttemptEntity> findByAttemptNo(String attemptNo);
    Optional<PaymentAttemptEntity> findByMerchantOrderNo(String merchantOrderNo);
    Optional<PaymentAttemptEntity> findByThirdPartyTransactionNo(String thirdPartyTransactionNo);
    Optional<PaymentAttemptEntity> findByIdempotencyKey(String idempotencyKey);
    List<PaymentAttemptEntity> findByPaymentIdOrderByCreatedAtAsc(Long paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from PaymentAttemptEntity a where a.attemptNo = :attemptNo")
    Optional<PaymentAttemptEntity> findByAttemptNoForUpdate(@Param("attemptNo") String attemptNo);
}
