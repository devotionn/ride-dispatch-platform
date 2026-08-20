package com.funccrypto.ridedispatch.driver;

import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<VehicleEntity, Long> {

    boolean existsByPlateNo(String plateNo);
}
