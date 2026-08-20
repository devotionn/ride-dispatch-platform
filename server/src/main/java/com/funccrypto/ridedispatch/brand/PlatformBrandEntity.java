package com.funccrypto.ridedispatch.brand;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "platform_brand")
public class PlatformBrandEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_name", nullable = false, length = 120)
    private String companyName;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlatformBrandEntity() {
    }

    public PlatformBrandEntity(String companyName, String logoUrl, Long updatedBy, Instant updatedAt) {
        this.companyName = companyName;
        this.logoUrl = logoUrl;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public void update(String companyName, String logoUrl, Long updatedBy, Instant now) {
        this.companyName = companyName;
        this.logoUrl = logoUrl;
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
