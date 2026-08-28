package com.funccrypto.ridedispatch.settlement;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverLedgerRepository extends JpaRepository<DriverLedgerEntity, Long> {
    Optional<DriverLedgerEntity> findByEventKey(String eventKey);
    List<DriverLedgerEntity> findByDriverIdOrderByCreatedAtDescIdDesc(Long driverId);
}
