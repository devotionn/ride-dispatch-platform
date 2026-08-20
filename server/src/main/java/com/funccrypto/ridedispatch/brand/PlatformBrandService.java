package com.funccrypto.ridedispatch.brand;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import com.funccrypto.ridedispatch.audit.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformBrandService {

    private final PlatformBrandRepository repository;
    private final AuditService auditService;
    private final Clock clock;
    private final String defaultCompanyName;

    public PlatformBrandService(
            PlatformBrandRepository repository,
            AuditService auditService,
            Clock clock,
            @Value("${app.brand.default-company-name:Ride Dispatch Platform}") String defaultCompanyName) {
        this.repository = repository;
        this.auditService = auditService;
        this.clock = clock;
        this.defaultCompanyName = defaultCompanyName;
    }

    @Transactional(readOnly = true)
    public BrandView get() {
        return repository.findFirstByOrderByIdAsc()
                .map(BrandView::from)
                .orElse(new BrandView(null, defaultCompanyName, null, null));
    }

    @Transactional
    public BrandView update(String companyName, String logoUrl, Long operatorId, String requestId) {
        Instant now = clock.instant();
        BrandView before = get();
        PlatformBrandEntity entity = repository.findFirstByOrderByIdAsc()
                .orElseGet(() -> new PlatformBrandEntity(companyName, logoUrl, operatorId, now));
        entity.update(companyName, logoUrl, operatorId, now);
        repository.save(entity);
        BrandView after = BrandView.from(entity);
        auditService.log(
                "ADMIN", operatorId, "PLATFORM_BRAND", entity.getId().toString(), "BRAND_UPDATED",
                Map.of("companyName", before.companyName()),
                Map.of("companyName", after.companyName()),
                null, requestId, now);
        return after;
    }

    public record BrandView(Long id, String companyName, String logoUrl, Instant updatedAt) {
        static BrandView from(PlatformBrandEntity entity) {
            return new BrandView(entity.getId(), entity.getCompanyName(), entity.getLogoUrl(), entity.getUpdatedAt());
        }
    }
}
