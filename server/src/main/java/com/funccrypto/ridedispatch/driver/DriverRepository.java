package com.funccrypto.ridedispatch.driver;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<DriverEntity, Long> {

    Optional<DriverEntity> findByQrShortCode(String qrShortCode);

    List<DriverEntity> findByAccountStatusAndWorkStatusAndAvailablePassengersGreaterThanEqual(
            DriverAccountStatus accountStatus,
            DriverWorkStatus workStatus,
            int passengerCount);
}
