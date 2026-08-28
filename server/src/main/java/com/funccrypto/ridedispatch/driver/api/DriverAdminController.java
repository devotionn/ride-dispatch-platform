package com.funccrypto.ridedispatch.driver.api;

import java.util.List;

import com.funccrypto.ridedispatch.auth.AuthenticatedPrincipal;
import com.funccrypto.ridedispatch.driver.DriverAdminService;
import com.funccrypto.ridedispatch.driver.DriverAccountStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/drivers")
@PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
public class DriverAdminController {

    private final DriverAdminService service;

    public DriverAdminController(DriverAdminService service) {
        this.service = service;
    }

    @GetMapping
    List<DriverAdminService.DriverView> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DriverAdminService.DriverView create(
            @Valid @RequestBody CreateDriverRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();
        return service.create(new DriverAdminService.CreateDriverCommand(
                        request.driverNo(), request.name(), request.mobile(), request.password(),
                        request.maxPassengers(), request.availablePassengers(),
                        request.plateNo(), request.brandModel()),
                principal.principalId(),
                requestId(servletRequest));
    }

    @GetMapping("/{id}")
    DriverAdminService.DriverDetailView detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @GetMapping("/{id}/qr")
    DriverAdminService.QrView qr(@PathVariable Long id) {
        return service.qr(id);
    }

    @PutMapping("/{id}")
    DriverAdminService.DriverView update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDriverRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();
        return service.update(id, new DriverAdminService.UpdateDriverCommand(
                        request.name(), request.mobile(), request.password(), request.maxPassengers(),
                        request.availablePassengers(), request.plateNo(), request.brandModel()),
                principal.principalId(), requestId(servletRequest));
    }

    @PutMapping("/{id}/status")
    DriverAdminService.DriverView updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDriverStatusRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();
        return service.updateAccountStatus(id, request.accountStatus(), principal.principalId(), requestId(servletRequest));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? null : value.toString();
    }

    public record CreateDriverRequest(
            @NotBlank @Size(max = 50) String driverNo,
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确") String mobile,
            @NotBlank @Size(min = 8, max = 100) String password,
            @Min(1) @Max(20) int maxPassengers,
            @Min(0) @Max(20) int availablePassengers,
            @NotBlank @Size(max = 32) String plateNo,
            @Size(max = 120) String brandModel) {
    }

    public record UpdateDriverRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确") String mobile,
            @Size(min = 8, max = 100) String password,
            @Min(1) @Max(20) int maxPassengers,
            @Min(0) @Max(20) int availablePassengers,
            @NotBlank @Size(max = 32) String plateNo,
            @Size(max = 120) String brandModel) {
    }

    public record UpdateDriverStatusRequest(@jakarta.validation.constraints.NotNull DriverAccountStatus accountStatus) {
    }
}
