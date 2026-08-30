package com.funccrypto.ridedispatch.place.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.funccrypto.ridedispatch.place.PlaceCatalogEntity;
import com.funccrypto.ridedispatch.place.PlaceCatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlaceCatalogController {

    private final PlaceCatalogService service;

    public PlaceCatalogController(PlaceCatalogService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/public/places/search")
    List<PlaceView> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit) {
        return service.search(q, limit).stream().map(PlaceView::from).toList();
    }

    @GetMapping("/api/v1/admin/places")
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    List<PlaceView> list() {
        return service.list().stream().map(PlaceView::from).toList();
    }

    @PostMapping("/api/v1/admin/places")
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    PlaceView create(@Valid @RequestBody PlaceRequest request) {
        return PlaceView.from(service.create(request.toCommand()));
    }

    @PutMapping("/api/v1/admin/places/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    PlaceView update(@PathVariable Long id, @Valid @RequestBody PlaceRequest request) {
        return PlaceView.from(service.update(id, request.toCommand()));
    }

    @PatchMapping("/api/v1/admin/places/{id}/enabled")
    @PreAuthorize("hasAnyRole('ADMIN','DISPATCHER')")
    PlaceView setEnabled(@PathVariable Long id, @Valid @RequestBody EnabledRequest request) {
        return PlaceView.from(service.setEnabled(id, request.enabled()));
    }

    public record PlaceRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 255) String addressText,
            BigDecimal latitude,
            BigDecimal longitude,
            @Size(max = 80) String city,
            @Size(max = 80) String district,
            @Size(max = 60) String category,
            @Size(max = 500) String aliases) {
        PlaceCatalogService.Command toCommand() {
            return new PlaceCatalogService.Command(name, addressText, latitude, longitude, city, district, category, aliases);
        }
    }

    public record EnabledRequest(@NotNull Boolean enabled) {}

    public record PlaceView(
            Long id,
            String name,
            String addressText,
            BigDecimal latitude,
            BigDecimal longitude,
            String coordinateSystem,
            String city,
            String district,
            String category,
            String aliases,
            boolean enabled,
            long usageCount,
            Instant lastUsedAt) {
        static PlaceView from(PlaceCatalogEntity place) {
            return new PlaceView(
                    place.getId(), place.getName(), place.getAddressText(), place.getLatitude(), place.getLongitude(),
                    place.getCoordinateSystem(), place.getCity(), place.getDistrict(), place.getCategory(),
                    place.getAliases(), place.isEnabled(), place.getUsageCount(), place.getLastUsedAt());
        }
    }
}
