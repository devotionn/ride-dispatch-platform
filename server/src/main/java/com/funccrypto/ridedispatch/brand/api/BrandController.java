package com.funccrypto.ridedispatch.brand.api;

import com.funccrypto.ridedispatch.auth.AuthenticatedPrincipal;
import com.funccrypto.ridedispatch.brand.PlatformBrandService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BrandController {

    private final PlatformBrandService service;

    public BrandController(PlatformBrandService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/public/brand")
    PlatformBrandService.BrandView publicBrand() {
        return service.get();
    }

    @GetMapping("/api/v1/admin/brand")
    PlatformBrandService.BrandView adminBrand() {
        return service.get();
    }

    @PutMapping("/api/v1/admin/brand")
    @PreAuthorize("hasRole('ADMIN')")
    PlatformBrandService.BrandView update(
            @Valid @RequestBody UpdateBrandRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();
        return service.update(
                request.companyName(),
                request.logoUrl(),
                principal.principalId(),
                requestId(servletRequest));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? null : value.toString();
    }

    public record UpdateBrandRequest(
            @NotBlank @Size(max = 120) String companyName,
            @Size(max = 500) String logoUrl) {
    }
}
