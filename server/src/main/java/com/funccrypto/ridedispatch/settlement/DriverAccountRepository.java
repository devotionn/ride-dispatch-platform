package com.funccrypto.ridedispatch.settlement;

import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriverAccountRepository extends JpaRepository<DriverAccountEntity, Long> {
    Optional<DriverAccountEntity> findByDriverId(Long driverId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from DriverAccountEntity a where a.driverId = :driverId")
    Optional<DriverAccountEntity> findByDriverIdForUpdate(@Param("driverId") Long driverId);
}
