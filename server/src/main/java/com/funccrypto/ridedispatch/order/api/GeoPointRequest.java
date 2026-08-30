package com.funccrypto.ridedispatch.order.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;

public record GeoPointRequest(
        @NotBlank String address,
        @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude) {

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }

    @AssertTrue(message = "经纬度必须同时填写或同时留空")
    public boolean isCoordinatePairComplete() {
        return (latitude == null) == (longitude == null);
    }
}
