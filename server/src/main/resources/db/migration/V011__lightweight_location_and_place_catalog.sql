ALTER TABLE ride_order
    MODIFY pickup_latitude DECIMAL(10,7) NULL,
    MODIFY pickup_longitude DECIMAL(10,7) NULL,
    MODIFY destination_latitude DECIMAL(10,7) NULL,
    MODIFY destination_longitude DECIMAL(10,7) NULL;

CREATE TABLE place_catalog (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    address_text VARCHAR(255) NOT NULL,
    latitude DECIMAL(10,7) NULL,
    longitude DECIMAL(10,7) NULL,
    coordinate_system VARCHAR(20) NOT NULL DEFAULT 'WGS84',
    city VARCHAR(80) NULL,
    district VARCHAR(80) NULL,
    category VARCHAR(60) NULL,
    aliases VARCHAR(500) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    usage_count BIGINT NOT NULL DEFAULT 0,
    last_used_at TIMESTAMP(6) NULL,
    source VARCHAR(30) NOT NULL DEFAULT 'ADMIN',
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    INDEX idx_place_catalog_name (name),
    INDEX idx_place_catalog_enabled_usage (enabled, usage_count),
    INDEX idx_place_catalog_city_district (city, district)
);
