package com.funccrypto.ridedispatch.order;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderProgressEventRepository extends JpaRepository<OrderProgressEventEntity, Long> {

    List<OrderProgressEventEntity> findByOrderIdOrderByOccurredAtAsc(Long orderId);
}
