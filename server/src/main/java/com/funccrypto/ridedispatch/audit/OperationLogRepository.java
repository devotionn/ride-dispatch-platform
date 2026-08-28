package com.funccrypto.ridedispatch.audit;

import java.util.List;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationLogRepository extends JpaRepository<OperationLogEntity, Long> {
    List<OperationLogEntity> findByObjectTypeAndObjectIdOrderByCreatedAtAscIdAsc(String objectType, String objectId);

    @Query("""
            select log from OperationLogEntity log
            where (:operatorType is null or log.operatorType = :operatorType)
              and (:objectType is null or log.objectType = :objectType)
              and (:objectId is null or log.objectId = :objectId)
              and (:action is null or log.action = :action)
              and (:fromTime is null or log.createdAt >= :fromTime)
              and (:toTime is null or log.createdAt < :toTime)
            order by log.createdAt desc, log.id desc
            """)
    Page<OperationLogEntity> search(
            @Param("operatorType") String operatorType,
            @Param("objectType") String objectType,
            @Param("objectId") String objectId,
            @Param("action") String action,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime,
            Pageable pageable);
}
