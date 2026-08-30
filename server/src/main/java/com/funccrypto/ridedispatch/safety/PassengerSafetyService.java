package com.funccrypto.ridedispatch.safety;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.funccrypto.ridedispatch.audit.AuditService;
import com.funccrypto.ridedispatch.order.PublicOrderService;
import com.funccrypto.ridedispatch.order.RideOrderEntity;
import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PassengerSafetyService {

    public static final Set<String> COMPLAINT_CATEGORIES = Set.of(
            "SERVICE_ATTITUDE", "ROUTE_DETOUR", "FEE_DISPUTE", "DRIVING_SAFETY", "VEHICLE_CONDITION", "OTHER");

    private final PublicOrderService publicOrderService;
    private final SafetyAlarmRepository alarmRepository;
    private final PassengerComplaintRepository complaintRepository;
    private final AuditService auditService;
    private final Clock clock;

    public PassengerSafetyService(
            PublicOrderService publicOrderService,
            SafetyAlarmRepository alarmRepository,
            PassengerComplaintRepository complaintRepository,
            AuditService auditService,
            Clock clock) {
        this.publicOrderService = publicOrderService;
        this.alarmRepository = alarmRepository;
        this.complaintRepository = complaintRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public SafetyAlarmEntity reportAlarm(
            String orderNo,
            String passengerToken,
            String sourcePage,
            java.math.BigDecimal latitude,
            java.math.BigDecimal longitude,
            String locationText) {
        RideOrderEntity order = null;
        if (orderNo != null && !orderNo.isBlank()) {
            if (passengerToken == null || passengerToken.isBlank()) {
                throw new BusinessException("PASSENGER_TOKEN_REQUIRED", "缺少订单访问凭据");
            }
            order = publicOrderService.getForPassenger(orderNo, passengerToken);
        }
        Instant now = clock.instant();
        SafetyAlarmEntity alarm = new SafetyAlarmEntity(
                order,
                order == null ? null : order.getOrderNo(),
                normalizeSourcePage(sourcePage),
                latitude,
                longitude,
                locationText,
                order == null ? null : order.getPassengerMobile(),
                now);
        return alarmRepository.save(alarm);
    }

    @Transactional
    public PassengerComplaintEntity createComplaint(String orderNo, String passengerToken, String category,
            String description, String contactMobile) {
        if (category == null || !COMPLAINT_CATEGORIES.contains(category)) {
            throw new BusinessException("COMPLAINT_CATEGORY_INVALID", "投诉类型无效");
        }
        RideOrderEntity order = publicOrderService.getForPassenger(orderNo, passengerToken);
        Instant now = clock.instant();
        PassengerComplaintEntity complaint = new PassengerComplaintEntity(
                nextComplaintNo(),
                order,
                order.getOrderNo(),
                category,
                description,
                contactMobile,
                now);
        return complaintRepository.save(complaint);
    }

    @Transactional
    public PassengerComplaintEntity handle(String complaintNo, ComplaintStatus nextStatus, String note,
            Long operatorId) {
        if (nextStatus == ComplaintStatus.OPEN) {
            throw new BusinessException("COMPLAINT_STATUS_INVALID", "投诉状态无效");
        }
        PassengerComplaintEntity complaint = complaintRepository.findByComplaintNo(complaintNo)
                .orElseThrow(() -> new BusinessException("COMPLAINT_NOT_FOUND", "投诉不存在"));
        ComplaintStatus before = complaint.getStatus();
        Instant now = clock.instant();
        complaint.applyHandle(nextStatus, note, operatorId, now);
        auditService.log("ADMIN", operatorId, "PASSENGER_COMPLAINT", complaint.getComplaintNo(),
                "COMPLAINT_HANDLED", before.name(), nextStatus.name(), note, null, now);
        return complaint;
    }

    private String normalizeSourcePage(String sourcePage) {
        return sourcePage == null || sourcePage.isBlank() ? "UNKNOWN" : sourcePage.toUpperCase();
    }

    private String nextComplaintNo() {
        return "PC" + UUID.randomUUID().toString().replace("-", "").substring(0, 24).toUpperCase();
    }
}
