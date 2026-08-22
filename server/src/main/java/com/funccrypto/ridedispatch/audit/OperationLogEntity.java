package com.funccrypto.ridedispatch.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "operation_log")
public class OperationLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operator_type", nullable = false, length = 30)
    private String operatorType;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "object_type", nullable = false, length = 50)
    private String objectType;

    @Column(name = "object_id", nullable = false, length = 80)
    private String objectId;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(name = "before_json", columnDefinition = "TEXT")
    private String beforeJson;

    @Column(name = "after_json", columnDefinition = "TEXT")
    private String afterJson;

    @Column(length = 500)
    private String reason;

    @Column(name = "request_id", length = 80)
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OperationLogEntity() {
    }

    public OperationLogEntity(
            String operatorType,
            Long operatorId,
            String objectType,
            String objectId,
            String action,
            String beforeJson,
            String afterJson,
            String reason,
            String requestId,
            Instant createdAt) {
        this.operatorType = operatorType;
        this.operatorId = operatorId;
        this.objectType = objectType;
        this.objectId = objectId;
        this.action = action;
        this.beforeJson = beforeJson;
        this.afterJson = afterJson;
        this.reason = reason;
        this.requestId = requestId;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getOperatorType() { return operatorType; }
    public Long getOperatorId() { return operatorId; }
    public String getObjectType() { return objectType; }
    public String getObjectId() { return objectId; }
    public String getAction() { return action; }
    public String getBeforeJson() { return beforeJson; }
    public String getAfterJson() { return afterJson; }
    public String getReason() { return reason; }
    public String getRequestId() { return requestId; }
    public Instant getCreatedAt() { return createdAt; }
}