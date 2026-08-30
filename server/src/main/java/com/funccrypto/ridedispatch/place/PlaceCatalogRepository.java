package com.funccrypto.ridedispatch.place;

import java.util.List;
import java.time.Instant;
import java.math.BigDecimal;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceCatalogRepository extends JpaRepository<PlaceCatalogEntity, Long> {

    @Query("""
            select p from PlaceCatalogEntity p
            where p.enabled = true and (
                lower(p.name) like lower(concat('%', :q, '%')) or
                lower(coalesce(p.aliases, '')) like lower(concat('%', :q, '%')) or
                lower(p.addressText) like lower(concat('%', :q, '%'))
            )
            order by
                case when lower(p.name) = lower(:q) then 0
                     when lower(p.name) like lower(concat(:q, '%')) then 1
                     when lower(coalesce(p.aliases, '')) like lower(concat('%', :q, '%')) then 2
                     else 3 end,
                p.usageCount desc,
                p.lastUsedAt desc,
                p.name asc
            """)
    List<PlaceCatalogEntity> searchEnabled(@Param("q") String query, Pageable pageable);

    @Modifying
    @Query("""
            update PlaceCatalogEntity p
               set p.usageCount = p.usageCount + 1,
                   p.lastUsedAt = :now,
                   p.updatedAt = :now,
                   p.version = p.version + 1
             where p.id = :id
               and p.enabled = true
               and p.addressText = :addressText
               and ((p.latitude = :latitude) or (p.latitude is null and :latitude is null))
               and ((p.longitude = :longitude) or (p.longitude is null and :longitude is null))
            """)
    int incrementUsageIfMatching(
            @Param("id") Long id,
            @Param("addressText") String addressText,
            @Param("latitude") BigDecimal latitude,
            @Param("longitude") BigDecimal longitude,
            @Param("now") Instant now);
}
