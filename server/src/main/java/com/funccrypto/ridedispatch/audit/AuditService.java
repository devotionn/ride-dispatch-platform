package com.funccrypto.ridedispatch.audit;

import java.time.Instant;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final OperationLogRepository repository;
    private final JsonMapper jsonMapper;

    public AuditService(OperationLogRepository repository, JsonMapper jsonMapper) {
        this.repository = repository;
        this.jsonMapper = jsonMapper;
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
            return jsonMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize audit snapshot", exception);
        }
    }
}
