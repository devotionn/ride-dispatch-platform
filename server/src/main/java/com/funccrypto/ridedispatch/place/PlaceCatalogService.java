package com.funccrypto.ridedispatch.place;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

import com.funccrypto.ridedispatch.shared.error.BusinessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlaceCatalogService {

    private final PlaceCatalogRepository repository;
    private final Clock clock;

    public PlaceCatalogService(PlaceCatalogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PlaceCatalogEntity> search(String query, int limit) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2) return List.of();
        int safeLimit = Math.max(1, Math.min(limit, 20));
        return repository.searchEnabled(q, PageRequest.of(0, safeLimit));
    }

    @Transactional(readOnly = true)
    public List<PlaceCatalogEntity> list() {
        return repository.findAll().stream()
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList();
    }

    @Transactional
    public PlaceCatalogEntity create(Command command) {
        validate(command);
        return repository.save(new PlaceCatalogEntity(
                command.name(), command.addressText(), command.latitude(), command.longitude(),
                command.city(), command.district(), command.category(), command.aliases(),
                "ADMIN", clock.instant()));
    }

    @Transactional
    public PlaceCatalogEntity update(Long id, Command command) {
        validate(command);
        PlaceCatalogEntity place = repository.findById(id)
                .orElseThrow(() -> new BusinessException("PLACE_NOT_FOUND", "地点不存在"));
        place.update(command.name(), command.addressText(), command.latitude(), command.longitude(),
                command.city(), command.district(), command.category(), command.aliases(), clock.instant());
        return place;
    }

    @Transactional
    public PlaceCatalogEntity setEnabled(Long id, boolean enabled) {
        PlaceCatalogEntity place = repository.findById(id)
                .orElseThrow(() -> new BusinessException("PLACE_NOT_FOUND", "地点不存在"));
        place.setEnabled(enabled, clock.instant());
        return place;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordUseIfEnabled(Long id, String addressText, BigDecimal latitude, BigDecimal longitude) {
        if (id == null) return;
        repository.incrementUsageIfMatching(
                id,
                addressText == null ? "" : addressText.trim(),
                latitude,
                longitude,
                clock.instant());
    }

    private void validate(Command command) {
        if (command.name() == null || command.name().isBlank()) {
            throw new BusinessException("PLACE_NAME_REQUIRED", "地点名称不能为空", HttpStatus.BAD_REQUEST);
        }
        if (command.addressText() == null || command.addressText().isBlank()) {
            throw new BusinessException("PLACE_ADDRESS_REQUIRED", "地点地址不能为空", HttpStatus.BAD_REQUEST);
        }
        boolean oneCoordinateMissing = (command.latitude() == null) != (command.longitude() == null);
        if (oneCoordinateMissing) {
            throw new BusinessException(
                    "PLACE_COORDINATES_INCOMPLETE", "经纬度必须同时填写或同时留空", HttpStatus.BAD_REQUEST);
        }
        if (command.latitude() != null
                && (command.latitude().compareTo(BigDecimal.valueOf(-90)) < 0
                || command.latitude().compareTo(BigDecimal.valueOf(90)) > 0)) {
            throw new BusinessException("PLACE_LATITUDE_INVALID", "纬度必须在 -90 到 90 之间", HttpStatus.BAD_REQUEST);
        }
        if (command.longitude() != null
                && (command.longitude().compareTo(BigDecimal.valueOf(-180)) < 0
                || command.longitude().compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new BusinessException("PLACE_LONGITUDE_INVALID", "经度必须在 -180 到 180 之间", HttpStatus.BAD_REQUEST);
        }
    }

    public record Command(
            String name,
            String addressText,
            BigDecimal latitude,
            BigDecimal longitude,
            String city,
            String district,
            String category,
            String aliases) {
    }
}
