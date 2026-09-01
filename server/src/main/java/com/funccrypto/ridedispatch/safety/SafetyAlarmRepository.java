package com.funccrypto.ridedispatch.safety;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SafetyAlarmRepository extends JpaRepository<SafetyAlarmEntity, Long> {

    List<SafetyAlarmEntity> findTop200ByOrderByCreatedAtDesc();
}
