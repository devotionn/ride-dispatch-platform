package com.funccrypto.ridedispatch.order;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PassengerOrderAccessTokenRepository extends JpaRepository<PassengerOrderAccessTokenEntity, Long> {

    boolean existsByOrderIdAndTokenHash(Long orderId, String tokenHash);
}
