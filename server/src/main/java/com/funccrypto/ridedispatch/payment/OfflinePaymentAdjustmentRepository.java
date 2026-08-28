package com.funccrypto.ridedispatch.payment;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OfflinePaymentAdjustmentRepository extends JpaRepository<OfflinePaymentAdjustmentEntity, Long> {
    Optional<OfflinePaymentAdjustmentEntity> findByIdempotencyKey(String idempotencyKey);
}
