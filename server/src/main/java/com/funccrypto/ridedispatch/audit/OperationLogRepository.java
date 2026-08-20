package com.funccrypto.ridedispatch.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationLogRepository extends JpaRepository<OperationLogEntity, Long> {
}
