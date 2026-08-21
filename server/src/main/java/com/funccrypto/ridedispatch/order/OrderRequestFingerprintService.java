package com.funccrypto.ridedispatch.order;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

@Component
public class OrderRequestFingerprintService {

    public String fingerprint(PublicOrderService.CreateOrderCommand command) {
        String canonical = String.join("\u001F",
                command.sourceType().name(),
                value(command.driverShortCode()),
                command.pickupAddress(),
                command.pickupLatitude().stripTrailingZeros().toPlainString(),
                command.pickupLongitude().stripTrailingZeros().toPlainString(),
                command.destinationAddress(),
                command.destinationLatitude().stripTrailingZeros().toPlainString(),
                command.destinationLongitude().stripTrailingZeros().toPlainString(),
                Integer.toString(command.passengerCount()),
                command.departureAt().toString(),
                command.passengerMobile(),
                value(command.remark()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
