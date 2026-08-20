package com.funccrypto.ridedispatch.driver;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DriverRepository extends JpaRepository<DriverEntity, Long> {

    Optional<DriverEntity> findByDriverNo(String driverNo);

    Optional<DriverEntity> findByQrShortCode(String qrShortCode);

    boolean existsByDriverNo(String driverNo);

    boolean existsByQrShortCode(String qrShortCode);

    List<DriverEntity> findAllByOrderByIdDesc();

    List<DriverEntity> findByAccountStatusAndWorkStatusAndAvailablePassengersGreaterThanEqual(
            DriverAccountStatus accountStatus,
            DriverWorkStatus workStatus,
            int passengerCount);
}
