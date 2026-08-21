package com.funccrypto.ridedispatch.order;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RideOrderRepository extends JpaRepository<RideOrderEntity, Long> {

    Optional<RideOrderEntity> findByOrderNo(String orderNo);

    Page<RideOrderEntity> findByStatus(OrderStatus status, Pageable pageable);

    List<RideOrderEntity> findByCurrentDriverIdAndStatusInOrderByDepartureAtAsc(
            Long currentDriverId,
            Collection<OrderStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from RideOrderEntity o where o.orderNo = :orderNo")
    Optional<RideOrderEntity> findByOrderNoForUpdate(@Param("orderNo") String orderNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from RideOrderEntity o where o.id = :id")
    Optional<RideOrderEntity> findByIdForUpdate(@Param("id") Long id);
}
