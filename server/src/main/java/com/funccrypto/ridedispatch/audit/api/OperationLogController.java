package com.funccrypto.ridedispatch.audit.api;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import com.funccrypto.ridedispatch.audit.OperationLogEntity;
import com.funccrypto.ridedispatch.audit.OperationLogRepository;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/operation-logs")
@PreAuthorize("hasAnyRole('ADMIN','DISPATCHER','FINANCE')")
public class OperationLogController {

    private final OperationLogRepository repository;

    public OperationLogController(OperationLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    PagedResponse list(
            @RequestParam(required = false) String operatorType,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) String objectId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(200) int size) {
        Page<OperationLogEntity> result = repository.search(
                normalize(operatorType), normalize(objectType), normalize(objectId), normalize(action),
                from == null ? null : from.toInstant(), to == null ? null : to.toInstant(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new PagedResponse(result.getContent().stream().map(LogView::from).toList(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record PagedResponse(List<LogView> content, int page, int size, long totalElements, int totalPages) {}

    public record LogView(
            Long id, String operatorType, Long operatorId, String objectType, String objectId,
            String action, String beforeJson, String afterJson, String reason, String requestId, Instant createdAt) {
        static LogView from(OperationLogEntity log) {
            return new LogView(log.getId(), log.getOperatorType(), log.getOperatorId(), log.getObjectType(),
                    log.getObjectId(), log.getAction(), log.getBeforeJson(), log.getAfterJson(), log.getReason(),
                    log.getRequestId(), log.getCreatedAt());
        }
    }
}
