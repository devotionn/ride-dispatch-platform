package com.funccrypto.ridedispatch.safety.api;

import java.time.Instant;
import java.util.List;

import com.funccrypto.ridedispatch.auth.AuthenticatedPrincipal;
import com.funccrypto.ridedispatch.safety.ComplaintStatus;
import com.funccrypto.ridedispatch.safety.PassengerComplaintEntity;
import com.funccrypto.ridedispatch.safety.PassengerComplaintRepository;
import com.funccrypto.ridedispatch.safety.PassengerSafetyService;
import com.funccrypto.ridedispatch.safety.SafetyAlarmEntity;
import com.funccrypto.ridedispatch.safety.SafetyAlarmRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
public class AdminPassengerSafetyController {

    private final PassengerSafetyService service;
    private final PassengerComplaintRepository complaintRepository;
    private final SafetyAlarmRepository alarmRepository;

    public AdminPassengerSafetyController(
            PassengerSafetyService service,
            PassengerComplaintRepository complaintRepository,
            SafetyAlarmRepository alarmRepository) {
        this.service = service;
        this.complaintRepository = complaintRepository;
        this.alarmRepository = alarmRepository;
    }

    @GetMapping("/passenger-complaints")
    List<ComplaintResponse> listComplaints(@RequestParam(required = false) ComplaintStatus status) {
        List<PassengerComplaintEntity> complaints = status == null
                ? complaintRepository.findAllByOrderByCreatedAtDesc()
                : complaintRepository.findAllByStatusOrderByCreatedAtDesc(status);
        return complaints.stream().map(ComplaintResponse::from).toList();
    }

    @PostMapping("/passenger-complaints/{complaintNo}/handle")
    ComplaintResponse handleComplaint(
            @PathVariable String complaintNo,
            @Valid @RequestBody HandleRequest request,
            Authentication authentication) {
        PassengerComplaintEntity complaint = service.handle(
                complaintNo,
                request.status(),
                request.note(),
                operatorId(authentication));
        return ComplaintResponse.from(complaint);
    }

    @GetMapping("/safety-alarms")
    List<AlarmResponse> listAlarms() {
        return alarmRepository.findTop200ByOrderByCreatedAtDesc().stream().map(AlarmResponse::from).toList();
    }

    private Long operatorId(Authentication authentication) {
        return ((AuthenticatedPrincipal) authentication.getPrincipal()).principalId();
    }

    public record HandleRequest(
            @NotNull ComplaintStatus status,
            @Size(max = 500) String note) {}

    public record ComplaintResponse(
            String complaintNo,
            String orderNo,
            String category,
            String description,
            String contactMobile,
            ComplaintStatus status,
            String handleNote,
            Long handledBy,
            Instant handledAt,
            Instant createdAt) {

        static ComplaintResponse from(PassengerComplaintEntity complaint) {
            return new ComplaintResponse(
                    complaint.getComplaintNo(),
                    complaint.getOrderNo(),
                    complaint.getCategory(),
                    complaint.getDescription(),
                    complaint.getContactMobile(),
                    complaint.getStatus(),
                    complaint.getHandleNote(),
                    complaint.getHandledBy(),
                    complaint.getHandledAt(),
                    complaint.getCreatedAt());
        }
    }

    public record AlarmResponse(
            Long alarmId,
            String orderNo,
            String sourcePage,
            java.math.BigDecimal latitude,
            java.math.BigDecimal longitude,
            String locationText,
            String passengerMobile,
            Instant createdAt) {

        static AlarmResponse from(SafetyAlarmEntity alarm) {
            return new AlarmResponse(
                    alarm.getId(),
                    alarm.getOrderNo(),
                    alarm.getSourcePage(),
                    alarm.getLatitude(),
                    alarm.getLongitude(),
                    alarm.getLocationText(),
                    alarm.getPassengerMobile(),
                    alarm.getCreatedAt());
        }
    }
}
