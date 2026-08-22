package com.funccrypto.ridedispatch.audit;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationLogRepository extends JpaRepository<OperationLogEntity, Long> {
    List<OperationLogEntity> findByObjectTypeAndObjectIdOrderByCreatedAtAscIdAsc(String objectType, String objectId);
}
