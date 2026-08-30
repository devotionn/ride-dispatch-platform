package com.funccrypto.ridedispatch.place;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "place_catalog")
public class PlaceCatalogEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 120) private String name;
    @Column(name = "address_text", nullable = false, length = 255) private String addressText;
    @Column(precision = 10, scale = 7) private BigDecimal latitude;
    @Column(precision = 10, scale = 7) private BigDecimal longitude;
    @Column(name = "coordinate_system", nullable = false, length = 20) private String coordinateSystem;
    @Column(length = 80) private String city;
    @Column(length = 80) private String district;
    @Column(length = 60) private String category;
    @Column(length = 500) private String aliases;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "usage_count", nullable = false) private long usageCount;
    @Column(name = "last_used_at") private Instant lastUsedAt;
    @Column(nullable = false, length = 30) private String source;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected PlaceCatalogEntity() {}

    public PlaceCatalogEntity(String name, String addressText, BigDecimal latitude, BigDecimal longitude,
            String city, String district, String category, String aliases, String source, Instant now) {
        this.name = normalizeRequired(name);
        this.addressText = normalizeRequired(addressText);
        this.latitude = latitude;
        this.longitude = longitude;
        this.coordinateSystem = "WGS84";
        this.city = normalizeOptional(city);
        this.district = normalizeOptional(district);
        this.category = normalizeOptional(category);
        this.aliases = normalizeOptional(aliases);
        this.source = source == null || source.isBlank() ? "ADMIN" : source.trim();
        this.enabled = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String name, String addressText, BigDecimal latitude, BigDecimal longitude,
            String city, String district, String category, String aliases, Instant now) {
        this.name = normalizeRequired(name);
        this.addressText = normalizeRequired(addressText);
        this.latitude = latitude;
        this.longitude = longitude;
        this.city = normalizeOptional(city);
        this.district = normalizeOptional(district);
        this.category = normalizeOptional(category);
        this.aliases = normalizeOptional(aliases);
        this.updatedAt = now;
    }

    public void setEnabled(boolean enabled, Instant now) {
        this.enabled = enabled;
        this.updatedAt = now;
    }

    public void markUsed(Instant now) {
        this.usageCount++;
        this.lastUsedAt = now;
        this.updatedAt = now;
    }

    private static String normalizeRequired(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAddressText() { return addressText; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public String getCoordinateSystem() { return coordinateSystem; }
    public String getCity() { return city; }
    public String getDistrict() { return district; }
    public String getCategory() { return category; }
    public String getAliases() { return aliases; }
    public boolean isEnabled() { return enabled; }
    public long getUsageCount() { return usageCount; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public String getSource() { return source; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
