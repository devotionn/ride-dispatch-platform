package com.funccrypto.ridedispatch.payment;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentExceptionRepository extends JpaRepository<PaymentExceptionEntity, Long> {

    List<PaymentExceptionEntity> findAllByOrderByCreatedAtDesc();

    Optional<PaymentExceptionEntity> findByExceptionNo(String exceptionNo);

    Optional<PaymentExceptionEntity> findByIdempotencyKey(String idempotencyKey);

    @Query("select coalesce(sum(e.requestedAmount), 0) from PaymentExceptionEntity e where e.paymentId = :paymentId and e.status <> :excludedStatus")
    long sumActiveRequestedAmount(@Param("paymentId") Long paymentId,
            @Param("excludedStatus") PaymentExceptionStatus excludedStatus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from PaymentExceptionEntity e where e.exceptionNo = :exceptionNo")
    Optional<PaymentExceptionEntity> findByExceptionNoForUpdate(@Param("exceptionNo") String exceptionNo);
}
