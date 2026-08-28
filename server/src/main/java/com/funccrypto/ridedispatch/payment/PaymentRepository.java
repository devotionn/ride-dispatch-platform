package com.funccrypto.ridedispatch.payment;

import java.util.Optional;
import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    Optional<PaymentEntity> findByPaymentNo(String paymentNo);
    Optional<PaymentEntity> findByOrderId(Long orderId);
    Optional<PaymentEntity> findByAccessTokenHash(String accessTokenHash);
    List<PaymentEntity> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.paymentNo = :paymentNo")
    Optional<PaymentEntity> findByPaymentNoForUpdate(@Param("paymentNo") String paymentNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.id = :id")
    Optional<PaymentEntity> findByIdForUpdate(@Param("id") Long id);
}
