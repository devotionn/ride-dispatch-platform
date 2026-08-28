package com.funccrypto.ridedispatch.driver.api;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.funccrypto.ridedispatch.auth.AuthenticatedPrincipal;
import com.funccrypto.ridedispatch.driver.DriverLocationSource;
import com.funccrypto.ridedispatch.driver.DriverSelfService;
import com.funccrypto.ridedispatch.driver.DriverWorkStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/driver/me")
public class DriverSelfController {

    private final DriverSelfService service;

    public DriverSelfController(DriverSelfService service) {
        this.service = service;
    }

    @GetMapping("/state")
    DriverSelfService.DriverStateView state(Authentication authentication) {
        return service.getState(driverId(authentication));
    }

    @PutMapping("/work-status")
    DriverSelfService.DriverStateView workStatus(
            @Valid @RequestBody WorkStatusRequest request,
            Authentication authentication) {
        return service.updateWorkStatus(driverId(authentication), request.workStatus());
    }

    @PutMapping("/available-passengers")
    DriverSelfService.DriverStateView availablePassengers(
            @Valid @RequestBody AvailablePassengersRequest request,
            Authentication authentication) {
        return service.updateAvailablePassengers(driverId(authentication), request.availablePassengers());
    }

    @PostMapping("/location")
    DriverSelfService.LocationView location(
            @Valid @RequestBody LocationRequest request,
            Authentication authentication) {
        return service.updateLocation(
                driverId(authentication),
                request.latitude(), request.longitude(), request.accuracyMeters(),
                request.locatedAt().toInstant(), request.source());
    }

    @GetMapping("/qr")
    DriverSelfService.QrView qr(Authentication authentication) {
        return service.getQr(driverId(authentication));
    }

    @GetMapping("/profile")
    DriverSelfService.ProfileView profile(Authentication authentication) {
        return service.getProfile(driverId(authentication));
    }

    private Long driverId(Authentication authentication) {
        return ((AuthenticatedPrincipal) authentication.getPrincipal()).principalId();
    }

    public record WorkStatusRequest(@NotNull DriverWorkStatus workStatus) {
    }

    public record AvailablePassengersRequest(@Min(0) @Max(20) int availablePassengers) {
    }

    public record LocationRequest(
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @DecimalMin("0") @DecimalMax("10000") BigDecimal accuracyMeters,
            @NotNull OffsetDateTime locatedAt,
            @NotNull DriverLocationSource source) {
    }
}
