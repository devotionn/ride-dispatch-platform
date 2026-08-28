package com.funccrypto.ridedispatch.brand.api;

import com.funccrypto.ridedispatch.auth.AuthenticatedPrincipal;
import com.funccrypto.ridedispatch.brand.BrandLogoStorage;
import com.funccrypto.ridedispatch.brand.PlatformBrandService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class BrandController {

    private final PlatformBrandService service;
    private final BrandLogoStorage logoStorage;

    public BrandController(
            PlatformBrandService service,
            BrandLogoStorage logoStorage) {
        this.service = service;
        this.logoStorage = logoStorage;
    }

    @GetMapping("/api/v1/public/brand")
    PlatformBrandService.BrandView publicBrand() {
        return service.get();
    }

    @GetMapping("/api/v1/public/brand/logo/{filename:.+}")
    ResponseEntity<Resource> logo(@PathVariable String filename) {
        BrandLogoStorage.StoredResource stored = logoStorage.load(filename);
        return ResponseEntity.ok()
                .contentType(stored.mediaType())
                .cacheControl(org.springframework.http.CacheControl.noCache())
                .body(stored.resource());
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

    @PostMapping(value = "/api/v1/admin/brand/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    PlatformBrandService.BrandView uploadLogo(
            @RequestPart("file") MultipartFile file,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) authentication.getPrincipal();
        BrandLogoStorage.StoredLogo stored = logoStorage.store(file);
        return service.updateLogo(stored.url(), principal.principalId(), requestId(servletRequest));
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
