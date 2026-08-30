package com.funccrypto.ridedispatch.place;

import java.util.List;

import org.springframework.data.domain.Pageable;
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
}
