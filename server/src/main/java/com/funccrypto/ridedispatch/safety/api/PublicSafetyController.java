package com.funccrypto.ridedispatch.safety.api;

import java.math.BigDecimal;

import com.funccrypto.ridedispatch.safety.ComplaintStatus;
import com.funccrypto.ridedispatch.safety.PassengerSafetyService;
import com.funccrypto.ridedispatch.safety.SafetyAlarmEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Passenger-facing safety endpoints. Both are intentionally usable without
 * authentication: an emergency alarm must never be blocked by an expired
 * token, and complaints are authorized by the order's passenger access token.
 */
@RestController
@RequestMapping("/api/v1/public/safety")
public class PublicSafetyController {

    private static final String PASSENGER_TOKEN_HEADER = "X-Passenger-Token";

    private final PassengerSafetyService service;

    public PublicSafetyController(PassengerSafetyService service) {
        this.service = service;
    }

    @PostMapping("/alarms")
    @ResponseStatus(HttpStatus.CREATED)
    AlarmCreatedResponse reportAlarm(@Valid @RequestBody AlarmRequest request) {
        SafetyAlarmEntity alarm = service.reportAlarm(
                request.orderNo(),
                request.passengerToken(),
                request.sourcePage(),
                request.latitude(),
                request.longitude(),
                request.locationText());
        return new AlarmCreatedResponse(alarm.getId(), alarm.getCreatedAt());
    }

    @PostMapping("/orders/{orderNo}/complaints")
    @ResponseStatus(HttpStatus.CREATED)
    ComplaintCreatedResponse createComplaint(
            @PathVariable String orderNo,
            @RequestHeader(PASSENGER_TOKEN_HEADER) String passengerToken,
            @Valid @RequestBody ComplaintRequest request) {
        var complaint = service.createComplaint(
                orderNo,
                passengerToken,
                request.category(),
                request.description(),
                request.contactMobile());
        return new ComplaintCreatedResponse(complaint.getComplaintNo(), complaint.getStatus().name());
    }

    public record AlarmRequest(
            @Size(max = 40) String orderNo,
            @Size(max = 200) String passengerToken,
            @NotBlank @Size(max = 40) String sourcePage,
            BigDecimal latitude,
            BigDecimal longitude,
            @Size(max = 255) String locationText) {}

    public record AlarmCreatedResponse(Long alarmId, java.time.Instant createdAt) {}

    public record ComplaintRequest(
            @NotBlank @Size(max = 40) String category,
            @NotBlank @Size(min = 5, max = 1000) String description,
            @Size(max = 30) String contactMobile) {}

    public record ComplaintCreatedResponse(String complaintNo, String status) {}
}
