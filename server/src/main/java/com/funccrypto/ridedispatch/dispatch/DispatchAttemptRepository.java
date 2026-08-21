package com.funccrypto.ridedispatch.dispatch;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DispatchAttemptRepository extends JpaRepository<DispatchAttemptEntity, Long> {

    Optional<DispatchAttemptEntity> findFirstByOrderIdAndStatusOrderByDispatchedAtDesc(Long orderId, DispatchAttemptStatus status);

    List<DispatchAttemptEntity> findByOrderIdOrderByDispatchedAtDesc(Long orderId);

    List<DispatchAttemptEntity> findByTargetDriverIdAndStatusOrderByDispatchedAtAsc(Long targetDriverId, DispatchAttemptStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from DispatchAttemptEntity a where a.id = :id")
    Optional<DispatchAttemptEntity> findByIdForUpdate(@Param("id") Long id);
}
