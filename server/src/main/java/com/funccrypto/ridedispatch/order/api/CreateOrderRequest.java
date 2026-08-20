package com.funccrypto.ridedispatch.order.api;

import java.time.OffsetDateTime;

import com.funccrypto.ridedispatch.order.OrderSourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @NotNull OrderSourceType sourceType,
        String driverShortCode,
        @NotNull @Valid GeoPointRequest pickup,
        @NotNull @Valid GeoPointRequest destination,
        @Min(1) @Max(20) int passengerCount,
        @NotNull OffsetDateTime departureAt,
        @NotBlank @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确") String mobile,
        @Size(max = 500) String remark) {
}
