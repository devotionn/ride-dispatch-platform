package com.funccrypto.ridedispatch.audit;

import java.time.Instant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final OperationLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(OperationLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void log(
            String operatorType,
            Long operatorId,
            String objectType,
            String objectId,
            String action,
            Object before,
            Object after,
            String reason,
            String requestId,
            Instant now) {
        repository.save(new OperationLogEntity(
                operatorType,
                operatorId,
                objectType,
                objectId,
                action,
                json(before),
                json(after),
                reason,
                requestId,
                now));
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize audit snapshot", exception);
        }
    }
}
