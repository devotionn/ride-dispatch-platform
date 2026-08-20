package com.funccrypto.ridedispatch.brand;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformBrandRepository extends JpaRepository<PlatformBrandEntity, Long> {

    Optional<PlatformBrandEntity> findFirstByOrderByIdAsc();
}
